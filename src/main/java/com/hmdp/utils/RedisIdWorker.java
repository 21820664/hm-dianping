package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.hmdp.utils.RedisConstants.HEAD;

/**
 * @name RedisIdWorker
 * @Description Redis实现全局唯一Id
 * @author WU
 * @Date 2022/11/7 13:57
 */
@Component
public class RedisIdWorker {
	/**
	 * 开始时间戳:2022-01-01T00:00:00的秒数
	 */
	private static final long BEGIN_TIMESTAMP = 1640995200L;
	/**
	 * 序列号的位数(参考位数图)
	 */
	private static final int COUNT_BITS = 32;
	
	private StringRedisTemplate stringRedisTemplate;
	
	public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}
	
	public long nextId(String keyPrefix) {
		// 1.生成时间戳
		LocalDateTime now = LocalDateTime.now();
		long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
		long timestamp = nowSecond - BEGIN_TIMESTAMP;//1970-01-01T00：00：00Z - 2022-01-01T00:00:00
		
		// 2.生成序列号
		// 2.1.获取当前日期，精确到天
		String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));//用 : 同时可以选择粒度大小
		// 2.2.自增长
		//忽略异常:不会导致空指针,会自增
		//获取指定key的值进行加1，如果value不是integer类型，会抛异常，如果key不存在会创建一个，默认value为0
		long count = stringRedisTemplate.opsForValue().increment(HEAD + "icr:" + keyPrefix + ":" + date);
		
		// 3.拼接并返回
		//位运算: timestamp先左移32位,让出count序列号位置.然后与count进行或运算(与0或相当于复制)[long为64位类型]
		return timestamp << COUNT_BITS | count;
	}
}
