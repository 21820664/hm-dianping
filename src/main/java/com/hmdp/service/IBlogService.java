package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
	
	/**
	 * @Description 通过ID查询Blog
	 * @Param [id]
	 * @return com.hmdp.dto.Result
	 */
	Result queryBlogById(Long id);
	
	/**
	 * @Description 通过排名查询热门博客
	 * @Param [current]
	 * @return com.hmdp.dto.Result
	 */
	Result queryHotBlog(Integer current);
	
	/**
	 * @Description 点赞博客
	 * @Param [id]
	 * @return com.hmdp.dto.Result
	 */
	Result likeBlog(Long id);
	
	/**
	 * @Description 查询博客最早点赞人
	 * @Param [id]
	 * @return com.hmdp.dto.Result
	 */
	Result queryBlogLikes(Long id);
}
