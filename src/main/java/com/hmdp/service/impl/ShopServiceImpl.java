package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
	
	/**
	 * @Description 根据商铺id查询商铺(Redis改写)
	 * @Param [id]
	 * @return com.hmdp.entity.Shop
	 */
	@Override
	public Result queryById(Long id) {
		
		String key = CACHE_SHOP_KEY + id;
		//1.从redis查询商铺缓存(使用Hash更方便,但为了练习此处使用String)
		String shopJson = stringRedisTemplate.opsForValue().get(key);
		//2.判断是否存在(命中)[空值为false,跳过]
		if (StrUtil.isNotBlank(shopJson)){
			Shop shop = JSONUtil.toBean(shopJson,Shop.class);
			return Result.ok(shop);
		}
		//3.存在("")，判断是否为空值
		if(shopJson != null){
			return Result.fail("店铺不存在！");
		}
		//4.不存在(null)，根据id查询数据库
		Shop shop = getById(id);
		//5.不存在，返回错误
		if (shop == null){
			//将空值写入redis,并缩短TTL时间
			stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
			return Result.fail("店铺不存在！");
		}
		//6.存在，写入redis, 并设置超时时间
		stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
		//7.返回
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
