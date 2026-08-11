package com.nhnacademy.insightongateway.common;

public enum RedisKey {

    BLACKLIST("blacklist:");

    private final String prefix;

    RedisKey(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
