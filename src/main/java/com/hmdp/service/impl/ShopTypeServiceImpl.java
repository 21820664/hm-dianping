package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
	
	@Resource
	private StringRedisTemplate stringRedisTemplate;
	
	/**
	 * @Description 查询商品类型列表(Redis改写)
	 * @Param []
	 * @return com.hmdp.dto.Result
	 */
	@Override
	public Result queryList() {
		//1.从redis查询商品类型列表缓存
		String shopTypeStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
		//2.判断是否存在
		if (StrUtil.isNotBlank(shopTypeStr)){
			//3,存在，直接返回
			List<ShopType> shopTypeList = JSONUtil.toList(shopTypeStr, ShopType.class);
			//String shopTypeJson = JSONUtil.toJsonStr(shopTypeStr);
			return Result.ok(shopTypeList);
		}
		//4.不存在，写入redis
		List<ShopType> typeList = query().orderByAsc("sort").list();
		String typeListJson = JSONUtil.toJsonStr(typeList);
		stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,typeListJson);
		//7.返回
		return Result.ok(typeList);
	}
}
