package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description 逻辑过期包装类
 */
@Data
public class RedisData {
    /*
     * @Description 过期时间
     */
    private LocalDateTime expireTime;
    private Object data;
}
