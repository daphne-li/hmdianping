package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 复述数据
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
