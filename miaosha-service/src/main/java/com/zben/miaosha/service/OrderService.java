package com.zben.miaosha.service;

/**
 * @DESC:
 * @AUTHOR: zhouben
 * @DATE: 2020/9/22 0022 9:26
 */
public interface OrderService {

    /**
     * 创建错误订单
     *
     * @param sid 库存ID
     * @return 订单ID
     */
    int createWrongOrder(int sid);

    /**
     * 乐观锁更新库存
     *
     * @param sid
     * @return
     */
    int createOptimisticOrder(int sid);

    /**
     * 携带hash的抢单接口
     *
     * @param sid
     * @param userId
     * @param verifyHash
     * @return
     */
    int createVerifiedOrder(Integer sid, Integer userId, String verifyHash) throws Exception;

    /**
     * 创建订单
     *
     * @param sid
     * @return
     */
    int createPessimisticOrder(int sid);

    /**
     * 检查缓存中用户是否下过单
     *
     * @param sid
     * @param userId
     * @return
     */
    Boolean checkUserOrderInfoInCache(Integer sid, Integer userId);

    /**
     * mq下单
     *
     * @param sid
     * @param userId
     */
    void createOrderByMq(Integer sid, Integer userId);
}
