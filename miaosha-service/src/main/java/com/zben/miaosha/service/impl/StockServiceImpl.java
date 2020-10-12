package com.zben.miaosha.service.impl;

import com.zben.miaosha.domain.Stock;
import com.zben.miaosha.mapper.StockMapper;
import com.zben.miaosha.service.StockService;
import com.zben.miaosha.service.utils.CacheKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import java.util.concurrent.TimeUnit;

/**
 * @DESC:库存服务
 * @author: zhouben
 * @date: 2020/9/22 0022 9:35
 */
@Service
@Slf4j
public class StockServiceImpl implements StockService {

    @Autowired
    StockMapper stockMapper;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    /**
     * 根据库存 ID 查询数据库库存信息
     *
     * @param id
     * @return
     */
    @Override
    public Stock getStockById(int id) {
        return stockMapper.selectByPrimaryKey(id);
    }

    /**
     * 修改库存
     *
     * @param stock
     * @return
     */
    @Override
    public int updateStockById(Stock stock) {
        return stockMapper.updateByPrimaryKeySelective(stock);
    }

    /**
     * 乐观锁更新库存
     *
     * @param stock
     * @return
     */
    @Override
    public int updateStockByOptimistic(Stock stock) {
        Example example = new Example(Stock.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("id", stock.getId());
        criteria.andEqualTo("version", stock.getVersion());
        Stock rec = stock;
        rec.setVersion(stock.getVersion() + 1);
        rec.setSale(stock.getSale() + 1);
        return stockMapper.updateByExample(rec, example);
    }

    /**
     * 从数据库中读取
     *
     * @param sid
     * @return
     */
    @Override
    public int getStockCountByDB(int sid) {
        Stock stock = stockMapper.selectByPrimaryKey(sid);
        return stock == null ? 0 : stock.getCount() - stock.getSale();
    }

    /**
     * 从缓存中读取
     * @param sid
     * @return
     */
    @Override
    public Integer getStockCountByCache(int sid) {
        String hashKey = CacheKey.STOCK_COUNT.getKey() + "_" + sid;
        String countStr = stringRedisTemplate.opsForValue().get(hashKey);
        if (countStr != null) {
            return Integer.parseInt(countStr);
        } else {
            return null;
        }
    }

    /**
     * 写入缓存
     * @param sid
     * @param count
     */
    @Override
    public void setStockCountToCache(int sid, Integer count) {
        String hashKey = CacheKey.STOCK_COUNT.getKey() + "_" + sid;
        log.info("写入商品库存缓存: [{}] [{}]", hashKey, String.valueOf(count));
        stringRedisTemplate.opsForValue().set(hashKey, String.valueOf(count), 3600, TimeUnit.SECONDS);
    }

    /**
     * 删除缓存
     * @param sid
     */
    @Override
    public boolean delStockCountCache(int sid) {
        String hashKey = CacheKey.STOCK_COUNT.getKey() + "_" + sid;
        try {
            stringRedisTemplate.delete(hashKey);
            log.info("删除商品id：[{}] 缓存", sid);
        } catch (Exception e) {
            log.error("删除商品id：[{}] 缓存失败 message: [{}]", sid, e.getMessage());
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    /**
     * 检查缓存中的库存
     * @param sid
     * @return
     */
    @Override
    public Integer getStockCount(Integer sid) {
        return null;
    }
}
