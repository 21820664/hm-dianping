package com.hmdp.utils;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.lang.UUID;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @name SimpleRedisLock
 * @Description Redis分布式锁
 * @author WU
 * @Date 2022/11/15 10:36
 */
public class SimpleRedisLock implements ILock {
	
	private String name;
	private StringRedisTemplate stringRedisTemplate;
	
	public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
		this.name = name;
		this.stringRedisTemplate = stringRedisTemplate;
	}
	
	private static final String KEY_PREFIX = "hmdp:lock:";
	private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
	
	//静态代码块加载lua
	private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
	static {
		UNLOCK_SCRIPT = new DefaultRedisScript<>();
		UNLOCK_SCRIPT.setLocation((Resource) new ClassPathResource("unlock.lua"));
		UNLOCK_SCRIPT.setResultType(Long.class);
	}
	
	@Override
	public boolean tryLock(long timeoutSec) {
		// 获取线程标示
		String threadId = ID_PREFIX + Thread.currentThread().getId();
		// 获取锁
		Boolean success = stringRedisTemplate.opsForValue()
				.setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
		return Boolean.TRUE.equals(success);
	}
	
	@Override
	public void unlock() {
		// 调用lua脚本[由于是调用脚本文件,确保了原子性]
		stringRedisTemplate.execute(
				UNLOCK_SCRIPT,
				Collections.singletonList(KEY_PREFIX + name),
				ID_PREFIX + Thread.currentThread().getId());
	}
	
    /*@Override
    public void unlock() {
        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁中的标示
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 判断标示是否一致
        if(threadId.equals(id)) {
            // 释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
