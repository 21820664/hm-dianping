package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {
	
	/**
	 * @Description 查询商品类型列表(Redis改写)
	 * @Param []
	 * @return com.hmdp.dto.Result
	 */
	Result queryList();
}
