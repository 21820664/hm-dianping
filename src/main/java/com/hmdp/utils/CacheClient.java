package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @name CacheClient
 * @Description 封装Redis工具类
 * @author WU
 * @Date 2022/11/1 17:59
 *
 * * 方法1：将任意Java对象序列化为json并==存储==在string类型的key中，并且可以**设置TTL过期时间**
 * * 方法2：将任意Java对象序列化为json并==存储==在string类型的key中，并且可以**设置逻辑过期时间**，用于处理缓存击穿问题
 * * 方法3：根据指定的key查询缓存，并==反序列化==为指定类型，利用**缓存空值**的方式解决缓存穿透问题
 * * 方法4：根据指定的key查询缓存，并==反序列化==为指定类型，需要利用**逻辑过期**解决缓存击穿问题
 */
@Slf4j
@Component
public class CacheClient {
	
	private final StringRedisTemplate stringRedisTemplate;
	
	private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
	
	public CacheClient(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}
	
	public void set(String key, Object value, Long time, TimeUnit unit) {
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
	}
	
	/**
	 * @Description 4.反序列化:进行逻辑过期包装(重建缓存)
	 * @Param [key, value, time, unit]
	 * @return void
	 */
	public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
		// 设置逻辑过期
		RedisData redisData = new RedisData();
		redisData.setData(value);
		redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
		// 写入Redis
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
	}
	
	/**
	 * @Description 3.反序列化:解决缓存穿透:Redis存储空值
	 * @Param [keyPrefix, id, type, dbFallback, time, unit]
	 * @return R
	 */
	public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
		
		String key = keyPrefix + id;
		// 1.从redis查询商铺缓存(使用Hash更方便,但为了练习此处使用String)
		String json = stringRedisTemplate.opsForValue().get(key);
		//2.判断是否存在(命中)[空值为false,跳过]
		if (StrUtil.isNotBlank(json)) {
			// 3.存在，直接返回
			return JSONUtil.toBean(json, type);
		}
		//3.存在("")，判断是否为空值
		if (json != null) {
			// 返回一个错误信息
			return null;
		}
		
		// 4.不存在，根据id查询数据库
		R r = dbFallback.apply(id);
		// 5.不存在，返回错误
		if (r == null) {
			//将空值写入redis,并缩短TTL时间
			stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
			// 返回错误信息
			return null;
		}
		// 6.存在，写入redis
		this.set(key, r, time, unit);
		return r;
	}
	
	/**
	 * @Description 2.序列化并设置逻辑时间解决缓存击穿
	 * @Param [keyPrefix, id, type, dbFallback, time, unit]
	 * @return R
	 */
	public <R, ID> R queryWithLogicalExpire(
			String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
		String key = keyPrefix + id;
		// 1.从redis查询商铺缓存
		String json = stringRedisTemplate.opsForValue().get(key);
		// 2.判断是否存在
		if (StrUtil.isBlank(json)) {
			// 3.存在，直接返回
			return null;
		}
		// 4.命中，需要先把json反序列化为对象
		RedisData redisData = JSONUtil.toBean(json, RedisData.class);
		R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
		LocalDateTime expireTime = redisData.getExpireTime();
		// 5.判断是否过期
		if(expireTime.isAfter(LocalDateTime.now())) {
			// 5.1.未过期，直接返回店铺信息
			return r;
		}
		// 5.2.已过期，需要缓存重建
		// 6.缓存重建
		// 6.1.获取互斥锁
		String lockKey = LOCK_SHOP_KEY + id;
		boolean isLock = tryLock(lockKey);
		// 6.2.判断是否获取锁成功
		if (isLock){
			// 6.3.成功，开启独立线程，实现缓存重建
			CACHE_REBUILD_EXECUTOR.submit(() -> {
				try {
					// 查询数据库
					R newR = dbFallback.apply(id);
					// 重建缓存
					this.setWithLogicalExpire(key, newR, time, unit);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}finally {
					// 释放锁
					unlock(lockKey);
				}
			});
		}
		// 6.4.返回过期的商铺信息
		return r;
	}
	
	/**
	 * @Description 1.序列化并设置互斥锁解决缓存击穿
	 * @Param [keyPrefix, id, type, dbFallback, time, unit]
	 * @return R
	 */
	public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
		String key = keyPrefix + id;
		// 1.从redis查询商铺缓存
		String shopJson = stringRedisTemplate.opsForValue().get(key);
		// 2.判断是否存在
		if (StrUtil.isNotBlank(shopJson)) {
			// 3.存在，直接返回
			return JSONUtil.toBean(shopJson, type);
		}
		// 判断命中的是否是空值
		if (shopJson != null) {
			// 返回一个错误信息
			return null;
		}
		
		// 4.实现缓存重建
		// 4.1.获取互斥锁
		String lockKey = LOCK_SHOP_KEY + id;
		R r = null;
		try {
			boolean isLock = tryLock(lockKey);
			// 4.2.判断是否获取成功
			if (!isLock) {
				// 4.3.获取锁失败，休眠并重试
				//模拟重建延时
				Thread.sleep(50);
				//!这块不加return 会造成部分线程获取到锁时进行数据库查询
				return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
			}
			// 4.4.获取锁成功，根据id查询数据库
			log.warn("获取锁成功，根据id查询数据库");
			r = dbFallback.apply(id);
			// 5.不存在，返回错误
			if (r == null) {
				// 将空值写入redis
				stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
				// 返回错误信息
				return null;
			}
			// 6.存在，写入redis
			this.set(key, r, time, unit);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}finally {
			// 7.释放锁
			unlock(lockKey);
		}
		// 8.返回
		return r;
	}
	
	private boolean tryLock(String key) {
		Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
		return BooleanUtil.isTrue(flag);
	}
	
	private void unlock(String key) {
		stringRedisTemplate.delete(key);
	}
}
