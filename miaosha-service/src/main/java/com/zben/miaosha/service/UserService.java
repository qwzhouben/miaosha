package com.zben.miaosha.service;

/**
 * @DESC:
 * @AUTHOR: zhouben
 * @DATE: 2020/9/23 0023 9:58
 */
public interface UserService {

    /**
     * 获取用户验证Hash
     *
     * @param sid    物品id
     * @param userId 用户id
     * @return
     * @throws Exception
     */
    String getVerifyHash(Integer sid, Integer userId) throws Exception;

    /**
     * 用户的访问次数
     *
     * @param userId
     * @return
     */
    boolean addUserCount(Integer userId);
}
