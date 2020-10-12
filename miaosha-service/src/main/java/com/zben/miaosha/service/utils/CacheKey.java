package com.zben.miaosha.service.utils;

public enum CacheKey {
    HASH_KEY("miaosha_user_hash"),
    LIMIT_KEY("miaosha_user_limit"),
    STOCK_COUNT("miaosha_stock_count"),
    USER_HAS_ORDER("miaosha_user_has_order");

    private String key;

    private CacheKey(String key) {
        this.key = key;
    }
    public String getKey() {
        return key;
    }
}