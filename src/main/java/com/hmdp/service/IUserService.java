package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IUserService extends IService<User> {
	
	/**
	 * @Description 发送短信验证码并保存验证码
	 * @Param [phone, session]
	 * @return com.hmdp.dto.Result
	 */
	Result sendCode(String phone, HttpSession session);
	
	/**
	 * @Description 实现登录功能
	 * @Param [loginForm, session]
	 * @return com.hmdp.dto.Result
	 */
	Result login(LoginFormDTO loginForm, HttpSession session);
	
	/**
	 * 当天签到功能
	 * @Param []
	 * @return com.hmdp.dto.Result
	 */
	Result sign();
	/**
	 * 签到统计功能(连续签到)
	 * @Param []
	 * @return com.hmdp.dto.Result
	 */
	Result signCount();
	
	
}
