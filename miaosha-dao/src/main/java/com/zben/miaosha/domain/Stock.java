package com.zben.miaosha.domain;

import lombok.Data;

import javax.persistence.Id;

/**
 * @DESC:库存
 * @author: zhouben
 * @date: 2020/9/22 0022 9:31
 */
@Data
public class Stock {

    //id
    @Id
    private Integer id;

    //名称
    private String name;

    //库存
    private Integer count;

    //已售
    private Integer sale;

    //乐观锁
    private Integer version;
}
