package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
	@Resource
	private ISeckillVoucherService seckillVoucherService;
	@Resource
	private RedisIdWorker redisIdWorker;
	@Resource
	private RedissonClient redissonClient;

	/**
	 * @Description 秒杀下单
	 * @Param [voucherId]
	 * @return com.hmdp.dto.Result
	 */
	@Override
	public Result seckillVoucher(Long voucherId) {
		// 1.查询优惠券
		SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
		// 2.判断秒杀是否开始
		//isAfter:检查BeginTime是否在LocalDateTime.now()之后
		if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
			// 尚未开始
			return Result.fail("秒杀尚未开始！");
		}
		// 3.判断秒杀是否已经结束
		if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
			// 已经结束
			return Result.fail("秒杀已经结束！");
		}
		// 4.判断库存是否充足
		if (voucher.getStock() < 1) {
			// 库存不足
			return Result.fail("库存不足！");
		}
		
		Long userId = UserHolder.getUser().getId();
		//创建锁对象 这个代码不用了，因为我们现在要使用分布式锁
		//SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
		RLock lock = redissonClient.getLock("hmdp:lock:order:" + userId);
		//获取锁对象(无参属于不等待)
		boolean isLock = lock.tryLock();
		
		//加锁失败
		if (!isLock) {
			return Result.fail("不允许重复下单");
		}
		try {
			//获取代理对象(事务)
			IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
			return proxy.createVoucherOrder(voucherId);
			//return createVoucherOrder(voucherId);
		} finally {
			//释放锁
			lock.unlock();
		}
		
		
	}
	
	/**
	 * @Description 创建优惠券订单
	 * @Param [voucherOrder]
	 * @return void
	 */
	private void createVoucherOrder(VoucherOrder voucherOrder) {
		Long userId = voucherOrder.getUserId();
		Long voucherId = voucherOrder.getVoucherId();
		// 创建锁对象
		RLock redisLock = redissonClient.getLock("lock:order:" + userId);
		// 尝试获取锁
		boolean isLock = redisLock.tryLock();
		// 判断
		if (!isLock) {
			// 获取锁失败，直接返回失败或者重试
			log.error("不允许重复下单！");
			return;
		}
		
		try {
			// 5.1.查询订单
			int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
			// 5.2.判断是否存在
			if (count > 0) {
				// 用户已经购买过了
				log.error("不允许重复下单！");
				return;
			}
			
			// 6.扣减库存
			boolean success = seckillVoucherService.update()
					.setSql("stock = stock - 1") // set stock = stock - 1
					.eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
					.update();
			if (!success) {
				// 扣减失败
				log.error("库存不足！");
				return;
			}
			
			// 7.创建订单
			save(voucherOrder);
		} finally {
			// 释放锁
			redisLock.unlock();
		}
	}
	
	/**
	 * @Description 创建优惠券订单(一人一单)
	 * @Param [voucherId]
	 * @return com.hmdp.dto.Result
	 */
	@Override
	@Transactional
	public Result createVoucherOrder(Long voucherId) {
		// 5.一人一单逻辑
		// 5.1.用户id
		Long userId = UserHolder.getUser().getId();
		
		int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
		// 5.2.判断是否存在
		if (count > 0) {
			// 用户已经购买过了
			return Result.fail("用户已经购买过一次！");
		}
		
		//log.info("版本号为:" + voucher.getVersion());
		//6，扣减库存
		//-扣减库存存入数据库
		//乐观锁:更新时才判断是否有其他线程在修改
		boolean success = seckillVoucherService.update()
				.setSql("stock= stock -1")
				.eq("voucher_id", voucherId).gt("stock", 0).update(); //where id = ? and stock > 0
			/*boolean success = seckillVoucherService.update()
			.setSql("stock= stock -1")// , version = version +1"
			.eq("voucher_id", voucherId)
			//.eq("version", voucher.getVersion())
			.update();//返回boolean*/
	
			/*LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
			updateWrapper.setSql("stock= stock -1")// , version = version +1"
					.eq(SeckillVoucher::getVoucherId, voucherId)
					.gt(SeckillVoucher::getStock,0)
					;//返回boolean
			boolean success = seckillVoucherService.update(voucher, updateWrapper);*/
				//log.info("扣减后,版本号为:" + voucher.getVersion());
		if (!success) {
			//扣减失败
			return Result.fail("扣减失败,库存可能不足！");
		}
		//7.创建订单
		VoucherOrder voucherOrder = new VoucherOrder();
		// 7.1.订单id
		long orderId = redisIdWorker.nextId("order");
		voucherOrder.setId(orderId);
		// 7.2.用户id
		userId = UserHolder.getUser().getId();
		voucherOrder.setUserId(userId);
		// 7.3.代金券id
		voucherOrder.setVoucherId(voucherId);
		//-新增订单存入数据库
		save(voucherOrder);
		
		return Result.ok(orderId);
		
	}
}
