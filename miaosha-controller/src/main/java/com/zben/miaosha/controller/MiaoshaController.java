package com.zben.miaosha.controller;

import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.RateLimiter;
import com.zben.miaosha.service.OrderService;
import com.zben.miaosha.service.StockService;
import com.zben.miaosha.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @DESC:
 * @author: zhouben
 * @date: 2020/9/22 0022 9:19
 */
@RestController
@Slf4j
public class MiaoshaController {

    @Autowired
    OrderService orderService;

    @Autowired
    UserService userService;

    @Autowired
    StockService stockService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * 令牌桶限流 每秒放行10个请求
     */
    RateLimiter rateLimiter = RateLimiter.create(10);

    // 延时时间：预估读数据库数据业务逻辑的耗时，用来做缓存再删除
    private static final int DELAY_MILLSECONDS = 1000;

    // 延时双删线程池
    private static ExecutorService cachedThreadPool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());


    /**
     * 获取验证值
     * 该接口要求传用户id和商品id，返回验证值，并且该验证值
     *
     * @return
     */
    @GetMapping("/getVerifyHash")
    public String getVerifyHash(@RequestParam(value = "sid") Integer sid,
                                @RequestParam(value = "userId") Integer userId) {
        String hash;
        try {
            hash = userService.getVerifyHash(sid, userId);
        } catch (Exception e) {
            log.error("获取验证hash失败，原因：[{}]", e.getMessage());
            return "获取验证hash失败";
        }
        return String.format("请求抢购验证hash值为：%s", hash);
    }

    /**
     * 下单1
     *
     * @param sid
     * @return
     */
    @GetMapping("/createWrongOrder/{sid}")
    public String createWrongOrder(@PathVariable int sid) {
        log.info("购买物品编号sid=[{}]", sid);
        int id = 0;
        try {
            id = orderService.createWrongOrder(sid);
            log.info("创建订单id: [{}]", id);
        } catch (Exception e) {
            log.error("Exception", e);
        }
        return String.valueOf(id);
    }

    /**
     * 乐观锁更新库存
     * @param sid
     * @return
     */
    /*@GetMapping("/createOptimisticOrder/{sid}")
    public String createOptimisticOrder(@PathVariable int sid) {
        int id;
        try {
            id = orderService.createOptimisticOrder(sid);
            log.info("购买成功，剩余库存为: [{}]", id);e
        } catch (Exception e) {
            log.error("购买失败：[{}]", e.getMessage());
            return "购买失败，库存不足";
        }
        return String.format("购买成功，剩余库存为：%d", id);
    }*/

    /**
     * 乐观锁更新库存 + 令牌桶限流
     *
     * @param sid
     * @return
     */
    @GetMapping("/createOptimisticOrder/{sid}")
    public String createOptimisticOrder(@PathVariable int sid) {
        //阻塞式获取令牌
        //log.info("等待时间" + rateLimiter.acquire());
        //非阻塞式获取令牌
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)) {
            log.warn("你被限流了，真不幸，直接返回失败");
            return "购买失败，库存不足";
        }
        int id;
        try {
            id = orderService.createOptimisticOrder(sid);
            log.info("购买成功，剩余库存为: [{}]", id);
        } catch (Exception e) {
            log.error("购买失败：[{}]", e.getMessage());
            return "购买失败，库存不足";
        }
        return String.format("购买成功，剩余库存为：%d", id);
    }

    /**
     * 要求验证的抢购接口
     *
     * @param sid
     * @return
     */
    @RequestMapping(value = "/createOrderWithVerifiedUrl", method = {RequestMethod.GET})
    public String createOrderWithVerifiedUrl(@RequestParam(value = "sid") Integer sid,
                                             @RequestParam(value = "userId") Integer userId,
                                             @RequestParam(value = "verifyHash") String verifyHash) {
        int stockLeft;
        try {
            stockLeft = orderService.createVerifiedOrder(sid, userId, verifyHash);
            log.info("购买成功，剩余库存为: [{}]", stockLeft);
        } catch (Exception e) {
            log.error("购买失败：[{}]", e.getMessage());
            return e.getMessage();
        }
        return String.format("购买成功，剩余库存为：%d", stockLeft);
    }

    /**
     * 要求验证的抢购接口 + 单用户限制访问频率
     *
     * @param sid
     * @return
     */
    @RequestMapping(value = "/createOrderWithVerifiedUrlAndLimit", method = {RequestMethod.GET})
    public String createOrderWithVerifiedUrlAndLimit(@RequestParam(value = "sid") Integer sid,
                                                     @RequestParam(value = "userId") Integer userId,
                                                     @RequestParam(value = "verifyHash") String verifyHash) {
        //非阻塞式获取令牌
        if (!rateLimiter.tryAcquire(1000, TimeUnit.MILLISECONDS)) {
            log.warn("你被限流了，真不幸，直接返回失败");
            return "购买失败，库存不足";
        }
        int stockLeft;
        try {
            boolean isBanned = userService.addUserCount(userId);
            if (isBanned) {
                log.warn("购买失败，超过频率限制");
                return "购买失败，超过频率限制";
            }
            stockLeft = orderService.createVerifiedOrder(sid, userId, verifyHash);
            log.info("购买成功，剩余库存为: [{}]", stockLeft);
        } catch (Exception e) {
            log.error("购买失败：[{}]", e.getMessage());
            return e.getMessage();
        }
        return String.format("购买成功，剩余库存为：%d", stockLeft);
    }

    /**
     * 查询库存：通过数据库查询库存
     *
     * @param sid
     * @return
     */
    @RequestMapping("/getStockByDB/{sid}")
    public String getStockByDB(@PathVariable int sid) {
        int count;
        try {
            count = stockService.getStockCountByDB(sid);
        } catch (Exception e) {
            log.error("查询库存失败：[{}]", e.getMessage());
            return "查询库存失败";
        }
        log.info("商品Id: [{}] 剩余库存为: [{}]", sid, count);
        return String.format("商品Id: %d 剩余库存为：%d", sid, count);
    }

    /**
     * 查询库存：通过缓存查询库存
     * 缓存命中：返回库存
     * 缓存未命中：查询数据库写入缓存并返回
     *
     * @param sid
     * @return
     */
    @RequestMapping("/getStockByCache/{sid}")
    public String getStockByCache(@PathVariable int sid) {
        Integer count;
        try {
            count = stockService.getStockCountByCache(sid);
            if (count == null) {
                count = stockService.getStockCountByDB(sid);
                log.info("缓存未命中，查询数据库，并写入缓存");
                stockService.setStockCountToCache(sid, count);
            }
        } catch (Exception e) {
            log.error("查询库存失败：[{}]", e.getMessage());
            return "查询库存失败";
        }
        log.info("商品Id: [{}] 剩余库存为: [{}]", sid, count);
        return String.format("商品Id: %d 剩余库存为：%d", sid, count);
    }

    /**
     * 下单接口：先删除缓存 在更新数据库
     *
     * @param sid
     * @return
     */
    @RequestMapping("/createOrderWithCacheV1/{sid}")
    public String createOrderWithCacheV1(@PathVariable int sid) {
        int count = 0;
        try {
            // 删除库存缓存
            stockService.delStockCountCache(sid);
            // 完成扣库存下单事务
            orderService.createPessimisticOrder(sid);
        } catch (Exception e) {
            log.error("购买失败：[{}]", e.getMessage());
            return "购买失败，库存不足";
        }
        log.info("购买成功，剩余库存为: [{}]", count);
        return String.format("购买成功，剩余库存为：%d", count);
    }

    /**
     * 下单接口：先更新数据库，再删缓存
     *
     * @param sid
     * @return
     */
    @RequestMapping("/createOrderWithCacheV2/{sid}")
    public String createOrderWithCacheV2(@PathVariable int sid) {
        int count = 0;
        try {
            // 完成扣库存下单事务
            orderService.createPessimisticOrder(sid);
            // 删除库存缓存
            stockService.delStockCountCache(sid);
        } catch (Exception e) {
            log.error("购买失败：[{}]", e.getMessage());
            return "购买失败，库存不足";
        }
        log.info("购买成功，剩余库存为: [{}]", count);
        return String.format("购买成功，剩余库存为：%d", count);
    }

    /**
     * 下单接口：先删除缓存，再更新数据库，缓存延时双删
     *
     * @param sid
     * @return
     */
    @RequestMapping("/createOrderWithCacheV3/{sid}")
    public String createOrderWithCacheV3(@PathVariable int sid) {
        int count;
        try {
            // 删除库存缓存
            stockService.delStockCountCache(sid);
            // 完成扣库存下单事务
            count = orderService.createPessimisticOrder(sid);
            // 延时指定时间后再次删除缓存
            cachedThreadPool.execute(new delCacheByThread(sid));
        } catch (Exception e) {
            log.error("购买失败：[{}]", e.getMessage());
            return "购买失败，库存不足";
        }
        log.info("购买成功，剩余库存为: [{}]", count);
        return String.format("购买成功，剩余库存为：%d", count);
    }

    /**
     * 下单接口：先更新数据库，再删缓存，删除缓存重试机制
     *
     * @param sid
     * @return
     */
    @RequestMapping("/createOrderWithCacheV4/{sid}")
    public String createOrderWithCacheV4(@PathVariable int sid) {
        int count;
        boolean b = false;
        try {
            // 完成扣库存下单事务
            count = orderService.createPessimisticOrder(sid);
            // 删除库存缓存
            b = stockService.delStockCountCache(sid);
            // 延时指定时间后再次删除缓存
            cachedThreadPool.execute(new delCacheByThread(sid));
        } catch (Exception e) {
            log.error("购买失败：[{}]", e.getMessage());
            if (!b) {
                // 假设上述再次删除缓存没成功，通知消息队列进行删除缓存
                sendToDelCache(String.valueOf(sid));
            }
            return "购买失败，库存不足";
        }
        log.info("购买成功，剩余库存为: [{}]", count);
        return String.format("购买成功，剩余库存为：%d", count);
    }

    /**
     * 下单接口：异步处理订单
     *
     * @param sid
     * @return
     */
    @RequestMapping(value = "/createUserOrderWithMq", method = {RequestMethod.GET})
    @ResponseBody
    public String createUserOrderWithMq(@RequestParam(value = "sid") Integer sid,
                                        @RequestParam(value = "userId") Integer userId) {
        try {
            // 检查缓存中该用户是否已经下单过
            Boolean hasOrder = orderService.checkUserOrderInfoInCache(sid, userId);
            if (hasOrder != null && hasOrder) {
                log.info("该用户已经抢购过");
                return "你已经抢购过了，不要太贪心.....";
            }
            // 没有下单过，检查缓存中商品是否还有库存
            log.info("没有抢购过，检查缓存中商品是否还有库存");
            Integer count = stockService.getStockCountByCache(sid);
            if (count <= 0) {
                return "秒杀请求失败，库存不足.....";
            }

            // 有库存，则将用户id和商品id封装为消息体传给消息队列处理
            // 注意这里的有库存和已经下单都是缓存中的结论，存在不可靠性，在消息队列中会查表再次验证
            log.info("有库存：[{}]", count);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("sid", sid);
            jsonObject.put("userId", userId);
            sendToOrderQueue(jsonObject.toJSONString());
            return "秒杀请求提交成功";
        } catch (Exception e) {
            log.error("下单接口：异步处理订单异常：", e);
            return "秒杀请求失败，服务器正忙.....";
        }
    }

    /**
     * 向消息队列发送下单请求消息
     *
     * @param message
     */
    public void sendToOrderQueue(String message) {
        log.info("这就去通知消息队列开始下单：[{}]", message);
        this.rabbitTemplate.convertAndSend("orderQueue", message);
    }

    /**
     * 向消息队列delCache发送消息
     *
     * @param message
     */
    private void sendToDelCache(String message) {
        log.info("这就去通知消息队列开始重试删除缓存：[{}]", message);
        this.rabbitTemplate.convertAndSend("delCache", message);
    }


    /**
     * 缓存再删除线程
     */
    private class delCacheByThread implements Runnable {
        private int sid;

        public delCacheByThread(int sid) {
            this.sid = sid;
        }

        public void run() {
            try {
                log.info("异步执行缓存再删除，商品id：[{}]， 首先休眠：[{}] 毫秒", sid, DELAY_MILLSECONDS);
                Thread.sleep(DELAY_MILLSECONDS);
                boolean b = stockService.delStockCountCache(sid);
                if (!b) {
                    sendToDelCache(String.valueOf(sid));
                }
                log.info("再次删除商品id：[{}] 缓存", sid);
            } catch (Exception e) {
                log.error("delCacheByThread执行出错", e);
            }
        }
    }
}






















