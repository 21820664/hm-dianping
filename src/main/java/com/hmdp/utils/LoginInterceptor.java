package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @name LoginInterceptor
 * @Description 登录拦截器
 * @author WU
 * @Date 2022/10/28 10:40
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
	
	/**
	 * 该方法是在执行执行servlet的 service方法之前执行的
	 * 即在进入controller之前调用
	 * @return 如果返回true表示继续执行下一个拦截器的PreHandle方法；如果没有拦截器了,则执行controller
	 *
	 * 只判断是否有用户
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		
		// 1.判断是否需要拦截（ThreadLocal中是否有用户）
		if (UserHolder.getUser() == null) {
			// 没有，需要拦截，设置状态码(未登录)
			response.setStatus(401);
			// 拦截
			return false;
		}
		// 有用户，则放行
		return true;
		
		
		
		/*%//1.获取session
		HttpSession session = request.getSession();
		//2.获取session中的用户
		Object user = session.getAttribute("user");
		//3.判断用户是否存在
		if(user == null){
			//4.不存在，拦截，返回401状态码
			response.setStatus(401);
			return false;
		}
		//5.存在，保存用户信息到Threadlocal
		log.info("进入Controller之前...保存用户");
		UserHolder.saveUser((UserDTO)user);
		//6.放行
		return true;*/
		
	}
	
}
