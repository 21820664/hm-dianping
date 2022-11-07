package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
	
	@Resource
	private StringRedisTemplate stringRedisTemplate;
	
	@Resource
	private CacheClient cacheClient;
	
	/**
	 * @Description 根据商铺id查询商铺(Redis改写)
	 * @Param [id]
	 * @return com.hmdp.entity.Shop
	 */
	@Override
	public Result queryById(Long id) {
		// 解决缓存穿透
		//Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
		
		// 互斥锁解决缓存击穿
		 Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
		
		// 逻辑过期解决缓存击穿
		// Shop shop = cacheClient
		//         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
		
		if (shop == null) {
			return Result.fail("店铺不存在！");
		}
		// 7.返回
		return Result.ok(shop);
	}
	
	
	/**
	 * @Description 根据id修改店铺(Redis改写)
	 * @Param [shop]
	 * @return void
	 */
	@Override
	public void updateByIds(Shop shop) {
		//1.先修改数据库
		updateById(shop);
		//2.再删除缓存
		String key = CACHE_SHOP_KEY + shop.getId();
		stringRedisTemplate.opsForValue().getOperations().delete(key);
	}
}
