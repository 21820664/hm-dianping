package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {
	/**
	 * @Description 关注和取关
	 * @Param [followUserId, isFollow]
	 * @return com.hmdp.dto.Result
	 */
	Result follow(Long followUserId, Boolean isFollow);
	/**
	 * 判断当前用户是否关注了该用户
	 * @param followUserId
	 * @return
	 */
	Result isFollow(Long followUserId);
	
	/**
	 * @Description 共同关注
	 * @Param [id]
	 * @return com.hmdp.dto.Result
	 */
	Result followCommons(Long id);
}
