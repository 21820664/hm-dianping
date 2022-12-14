package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @name RedissonConfig
 * @Description Redisson配置文件
 * @author WU
 * @Date 2022/11/15 18:16
 */
@Configuration
public class RedissonConfig {
	
	@Bean
	public RedissonClient redissonClient(){
		// 配置
		Config config = new Config();
		//config.useSingleServer().setAddress("redis://192.168.150.101:6379").setPassword("123321");
		config.useSingleServer().setAddress("redis://127.0.0.1:6379");
		// 创建RedissonClient对象
		return Redisson.create(config);
	}
}
