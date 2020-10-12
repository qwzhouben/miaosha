package com.zben.miaosha.mapper;

import com.zben.miaosha.domain.Stock;
import tk.mybatis.mapper.common.Mapper;

/**
 * @DESC:
 * @AUTHOR: zhouben
 * @DATE: 2020/9/22 0022 9:38
 */
public interface StockMapper extends Mapper<Stock> {

    /**
     * 乐观锁更新库存
     *
     * @param stock
     * @return
     */
    int updateByOptimistic(Stock stock);
}
