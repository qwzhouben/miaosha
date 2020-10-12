package com.zben.miaosha.service;

import com.zben.miaosha.domain.Stock;

/**
 * @DESC:
 * @AUTHOR: zhouben
 * @DATE: 2020/9/22 0022 9:35
 */
public interface StockService {

    /**
     * 根据库存 ID 查询数据库库存信息
     *
     * @param id
     * @return
     */
    Stock getStockById(int id);

    /**
     * 修改库存
     *
     * @param stock
     * @return
     */
    int updateStockById(Stock stock);

    /**
     * 乐观锁更新库存
     *
     * @param stock
     * @return
     */
    int updateStockByOptimistic(Stock stock);

    /**
     * 查询数据库
     *
     * @param sid
     * @return
     */
    int getStockCountByDB(int sid);

    /**
     * 从缓存中查询
     *
     * @param sid
     * @return
     */
    Integer getStockCountByCache(int sid);

    /**
     * 写入缓存
     *
     * @param sid
     * @param count
     */
    void setStockCountToCache(int sid, Integer count);

    /**
     * 删除缓存
     * @param sid
     */
    boolean delStockCountCache(int sid);

    /**
     * 检查缓存中是否还有库存
     * @param sid
     * @return
     */
    Integer getStockCount(Integer sid);
}
