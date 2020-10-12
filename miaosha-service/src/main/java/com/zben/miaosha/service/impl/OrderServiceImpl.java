package com.zben.miaosha.service.impl;

import com.zben.miaosha.domain.Stock;
import com.zben.miaosha.domain.StockOrder;
import com.zben.miaosha.domain.User;
import com.zben.miaosha.mapper.StockOrderMapper;
import com.zben.miaosha.service.OrderService;
import com.zben.miaosha.service.StockService;
import com.zben.miaosha.service.utils.CacheKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @DESC:订单服务
 * @author: zhouben
 * @date: 2020/9/22 0022 9:27
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    StockService stockService;

    @Autowired
    StockOrderMapper stockOrderMapper;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public int createWrongOrder(int sid) {
        //校验库存
        Stock stock = checkStock(sid);
        //扣库存
        saleStock(stock);
        //创建订单
        int id = createOrder(stock);
        return id;
    }

    /**
     * 乐观锁更新库存
     *
     * @param sid
     * @return
     */
    @Override
    public int createOptimisticOrder(int sid) {
        //校验库存
        Stock stock = checkStock(sid);
        //乐观锁更新库存
        saleStockOptimistic(stock);
        //创建订单
        createOrder(stock);
        return stock.getCount() - (stock.getSale() + 1);
    }

    /**
     * 需要验证的抢单接口
     *
     * @param sid        物品id
     * @param userId     用户id
     * @param verifyHash hash
     * @return
     */
    @Override
    public int createVerifiedOrder(Integer sid, Integer userId, String verifyHash) throws Exception {
        //验证是否在抢购时间内
        log.info("请自行验证是否在抢购时间内，假设此处验证成功");

        //验证hash值的合法性
        String hashKey = CacheKey.HASH_KEY.getKey() + "_" + sid + "_" + userId;
        String verifyHashInRedis = stringRedisTemplate.opsForValue().get(hashKey);
        if (!verifyHash.equals(verifyHashInRedis)) {
            throw new Exception("hash与redis中不符合");
        }
        log.info("验证hash值合法性成功");

        // 检查商品合法性
        Stock stock = stockService.getStockById(sid);
        if (stock == null) {
            throw new Exception("商品不存在");
        }
        log.info("商品信息验证成功：[{}]", stock.toString());

        //检查用户合法性
        //乐观锁更新库存
        saleStockOptimistic(stock);
        log.info("乐观锁更新库存成功");

        //创建订单
        createOrderWithUserInfo(stock, userId);
        log.info("创建订单成功");

        return stock.getCount() - (stock.getSale());
    }

    /**
     * 创建订单
     *
     * @param sid
     * @return
     */
    @Override
    public int createPessimisticOrder(int sid) {
        //校验库存
        Stock stock = checkStock(sid);
        //乐观锁更新库存
        saleStockOptimistic(stock);
        //创建订单
        createOrder(stock);
        return stock.getCount() - (stock.getSale());
    }

    /**
     * 查看缓存中是否有用户订单
     *
     * @param sid
     * @param userId
     * @return
     */
    @Override
    public Boolean checkUserOrderInfoInCache(Integer sid, Integer userId) {
        String key = CacheKey.USER_HAS_ORDER.getKey() + "_" + sid;
        log.info("检查用户Id：[{}] 是否抢购过商品Id：[{}] 检查Key：[{}]", userId, sid, key);
        return stringRedisTemplate.opsForSet().isMember(key, userId.toString());
    }

    /**
     * mq异步下单
     *
     * @param sid
     * @param userId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrderByMq(Integer sid, Integer userId) {
        Stock stock;
        //校验库存（不要学我在trycatch中做逻辑处理，这样是不优雅的。这里这样处理是为了兼容之前的秒杀系统文章）
        try {
            stock = checkStock(sid);
        } catch (Exception e) {
            log.info("库存不足！");
            return;
        }
        //乐观锁更新库存
        boolean updateStock = saleStockOptimistic(stock);
        if (!updateStock) {
            log.warn("扣减库存失败，库存已经为0");
            return;
        }

        log.info("扣减库存成功，剩余库存：[{}]", stock.getCount() - stock.getSale());
        stockService.delStockCountCache(sid);
        log.info("删除库存缓存");

        //创建订单
        log.info("写入订单至数据库");
        createOrderWithUserInfo(stock, userId);
        log.info("写入订单至缓存供查询");
        createOrderWithUserInfoInCache(stock, userId);
        log.info("下单完成");
    }

    /**
     * 写入订单至缓存
     *
     * @param stock
     * @param userId
     */
    public Long createOrderWithUserInfoInCache(Stock stock, Integer userId) {
        String key = CacheKey.USER_HAS_ORDER.getKey() + "_" + stock.getId().toString();
        log.info("写入用户订单数据Set：[{}] [{}]", key, userId.toString());
        return stringRedisTemplate.opsForSet().add(key, userId.toString());
    }

    /**
     * 乐观锁更新库存
     *
     * @param stock
     */
    public boolean saleStockOptimistic(Stock stock) {
        log.info("查询数据库，尝试更新库存");
        int count = stockService.updateStockByOptimistic(stock);
        if (count == 0) {
            throw new RuntimeException("并发更新库存失败，version不匹配");
        }
        return count > 0;
    }

    /**
     * 校验库存
     *
     * @param sid 物品id
     * @return
     */
    public Stock checkStock(int sid) {
        Stock stock = stockService.getStockById(sid);
        if (stock == null) {
            throw new RuntimeException("物品不存在");
        }
        if (stock.getSale().equals(stock.getCount())) {
            throw new RuntimeException("库存不足");
        }
        return stock;
    }

    /**
     * 扣减库存
     *
     * @param stock
     */
    public int saleStock(Stock stock) {
        stock.setSale(stock.getSale() + 1);
        return stockService.updateStockById(stock);
    }

    /**
     * 创建订单
     *
     * @param stock
     * @return
     */
    public int createOrder(Stock stock) {
        StockOrder order = new StockOrder();
        order.setSid(stock.getId());
        order.setName(stock.getName());
        int id = stockOrderMapper.insertSelective(order);
        return id;
    }

    /**
     * 创建订单
     *
     * @param stock
     * @param userId
     */
    public int createOrderWithUserInfo(Stock stock, Integer userId) {
        StockOrder order = new StockOrder();
        order.setSid(stock.getId());
        order.setName(stock.getName());
        order.setUserId(userId);
        return stockOrderMapper.insertSelective(order);
    }
}
