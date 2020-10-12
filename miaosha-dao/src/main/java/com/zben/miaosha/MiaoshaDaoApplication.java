package com.zben.miaosha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tk.mybatis.spring.annotation.MapperScan;

/**
 * @DESC:
 * @author: zhouben
 * @date: 2020/9/22 0022 9:29
 */
@SpringBootApplication
@MapperScan(basePackages = "com.zben.miaosha.mapper")
public class MiaoshaDaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiaoshaDaoApplication.class, args);
    }
}
