package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @name RefreshTokenInterceptor
 * @Description 第一个拦截器不拦截,只进行threadLocal的保存
 * @author WU
 * @Date 2022/10/29 14:40
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
	
	private final StringRedisTemplate stringRedisTemplate;
	
	public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}
	
	/**
	 * 该方法是在执行执行servlet的 service方法之前执行的
	 * 即在进入controller之前调用
	 * @return 如果返回true表示继续执行下一个拦截器的PreHandle方法；如果没有拦截器了,则执行controller
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		// 1.获取请求头中的token
		//如果登录会放行到LoginInterceptor,而登录拦截器配置了不拦截的路径:"/user/login",所以也会放行,不会被拦截
		String token = request.getHeader("authorization");
		if (StrUtil.isBlank(token)) {
			return true;
		}
		// 2.基于TOKEN获取redis中的用户
		String key  = LOGIN_USER_KEY + token;
		Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
		// 3.判断用户是否存在
		if (userMap.isEmpty()) {
			return true;
		}
		// 5.将查询到的hash数据转为UserDTO
		UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
		// 6.存在，保存用户信息到 ThreadLocal
		log.info("RefreshTokenInterceptor: 进入LoginInterceptor之前...保存用户");
		UserHolder.saveUser(userDTO);
		// 7.刷新token有效期
		stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
		// 8.放行
		return true;
	}
	
	/**
	 * 整个请求结束之后，即返回视图之后执行
	 * 该方法需要同一拦截器的preHandle返回true时执行，
	 * 如果该拦截器preHandle返回false，则该拦截器的afterCompletion不执行
	 * 执行顺序：先执行最后一个返回true的拦截器的afterCompletion，在依次向前
	 */
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		log.info("视图解析完成...移除用户");
		// 移除用户
		UserHolder.removeUser();
	}
}
