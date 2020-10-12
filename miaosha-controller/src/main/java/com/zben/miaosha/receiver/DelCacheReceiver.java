package com.zben.miaosha.receiver;

import com.zben.miaosha.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RabbitListener(queues = "delCache")
public class DelCacheReceiver {

    @Autowired
    private StockService stockService;

    @RabbitHandler
    public void process(String message) {
        log.info("DelCacheReceiver收到消息: " + message);
        log.info("DelCacheReceiver开始删除缓存: " + message);
        stockService.delStockCountCache(Integer.parseInt(message));
    }
}