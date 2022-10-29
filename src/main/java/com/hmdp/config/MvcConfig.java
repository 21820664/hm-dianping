package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @name MvcConfig
 * @Description 拦截器配置
 * @author WU
 * @Date 2022/10/28 15:04
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
	
	@Resource
	private StringRedisTemplate stringRedisTemplate;
	
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		
		// 第一个拦截器不拦截,只进行threadLocal的保存
		//token刷新的拦截器
		registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
				//addPathPatterns指定拦截器要拦截的路径(不写则默认拦截所有路径)
				.addPathPatterns("/**")
				//默认为0,值越大越靠后
				.order(0);
		
		// 登录拦截器
		registry.addInterceptor(new LoginInterceptor())
				//excludePathPatterns指定拦截器不拦截的路径
				.excludePathPatterns(
						"/shop/**",
						"/voucher/**",
						"/shop-type/**",
						"/upload/**",
						"/blog/hot",
						"/user/code",
						"/user/login"
				).order(1);

	}
}