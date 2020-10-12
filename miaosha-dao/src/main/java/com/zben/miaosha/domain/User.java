package com.zben.miaosha.domain;

import lombok.Data;

import javax.persistence.Id;

/**
 * @DESC:
 * @author: zhouben
 * @date: 2020/9/23 0023 10:00
 */
@Data
public class User {

    @Id
    private Long id;

    private String userName;
}
