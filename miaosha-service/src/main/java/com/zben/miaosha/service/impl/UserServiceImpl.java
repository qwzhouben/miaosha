package com.zben.miaosha.service.impl;

import com.zben.miaosha.domain.Stock;
import com.zben.miaosha.domain.User;
import com.zben.miaosha.mapper.UserMapper;
import com.zben.miaosha.service.StockService;
import com.zben.miaosha.service.UserService;
import com.zben.miaosha.service.utils.CacheKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * @DESC:用户服务
 * @author: zhouben
 * @date: 2020/9/23 0023 9:58
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    StockService stockService;

    @Autowired
    UserMapper userMapper;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    private static final String SALT = "randomString";
    private static final int ALLOW_COUNT = 5;

    /**
     * 获取用户验证Hash
     *
     * @param sid    物品id
     * @param userId 用户id
     * @return
     * @throws Exception
     */
    @Override
    public String getVerifyHash(Integer sid, Integer userId) throws Exception {
        // 验证是否在抢购时间内
        log.info("请自行验证是否在抢购时间内");


        // 检查用户合法性
        User user = userMapper.selectByPrimaryKey(userId.longValue());
        if (user == null) {
            throw new Exception("用户不存在");
        }
        log.info("用户信息：[{}]", user.toString());

        // 检查商品合法性
        Stock stock = stockService.getStockById(sid);
        if (stock == null) {
            throw new Exception("商品不存在");
        }
        log.info("商品信息：[{}]", stock.toString());

        // 生成hash
        String verify = SALT + sid + userId;
        String verifyHash = DigestUtils.md5DigestAsHex(verify.getBytes());

        // 将hash和用户商品信息存入redis
        String hashKey = CacheKey.HASH_KEY.getKey() + "_" + sid + "_" + userId;
        stringRedisTemplate.opsForValue().set(hashKey, verifyHash, 3600, TimeUnit.SECONDS);
        log.info("Redis写入：[{}] [{}]", hashKey, verifyHash);
        return verifyHash;
    }

    /**
     * 统计用户的访问次数
     *
     * @param userId
     * @return
     */
    @Override
    public boolean addUserCount(Integer userId) {
        String limitKey = CacheKey.LIMIT_KEY.getKey() + "_" + userId;
        String limitNum = stringRedisTemplate.opsForValue().get(limitKey);
        int limit = 0;
        if (StringUtils.isEmpty(limitNum)) {
            limit++;
        } else {
            limit = Integer.parseInt(limitNum) + 1;
        }
        stringRedisTemplate.opsForValue().set(limitKey, String.valueOf(limit), 3600, TimeUnit.SECONDS);
        log.info("用户截至该次的访问次数为: [{}]", limit);
        return limit > ALLOW_COUNT;
    }
}





















