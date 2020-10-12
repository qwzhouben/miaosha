package com.zben.miaosha.domain;

import lombok.Data;

import java.util.Date;

/**
 * @DESC:库存订单
 * @author: zhouben
 * @date: 2020/9/22 0022 10:22
 */
@Data
public class StockOrder {

    //id
    private Integer id;

    //物品id
    private Integer sid;

    //物品名称
    private String name;

    //用户id
    private Integer userId;

    //下单时间
    private Date createTime;
}
