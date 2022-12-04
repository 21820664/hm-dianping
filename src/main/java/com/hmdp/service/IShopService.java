package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {
	
	/**
	 * @Description 根据商铺id查询商铺(Redis改写)
	 * @Param [id]
	 * @return com.hmdp.entity.Shop
	 */
	Result queryById(Long id);
	
	/**
	 * @Description 根据id修改店铺(Redis改写)
	 * @Param [shop]
	 * @return void
	 */
	void updateByIds(Shop shop);
	
	/**
	 * 根据商铺类型分页查询商铺信息(可通过距离进行排序)
	 * @Param [typeId, current, x, y]
	 * @return com.hmdp.dto.Result
	 */
	Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
