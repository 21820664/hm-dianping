package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  优惠券订单服务实现类
 * </p>
 *
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
	@Resource
	private StringRedisTemplate stringRedisTemplate;
	
	//静态代码块加载lua
	private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
	static {
		SECKILL_SCRIPT = new DefaultRedisScript<>();
		SECKILL_SCRIPT.setLocation( new ClassPathResource("seckill.lua"));//此处用的是spring的不是hutool
		SECKILL_SCRIPT.setResultType(Long.class);
	}
	

	//异步处理线程池
	private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
	

	
	/**
	 * 在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
	 */
	@PostConstruct//后置处理
	private void init() {
		SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
	}
	
	// 用于线程池处理的任务
	// 当初始化完毕后，就会去从队列中去拿信息
	private class VoucherOrderHandler implements Runnable {
		//消息队列
		@Override
		public void run() {
			while (true) {
				try {
					// 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
					List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(//read:XREADGROUP
							Consumer.from("g1", "c1"),
							StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),//Duration[周期]
							StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
					);
					// 2.判断订单信息是否为空
					if (list == null || list.isEmpty()) {
						// 如果为null，说明没有消息，继续下一次循环
						continue;
					}
					// 解析数据(<String, Object, Object>因为返回的是键值对类型)
					MapRecord<String, Object, Object> record = list.get(0);
					Map<Object, Object> value = record.getValue();
					VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
					// 3.创建订单
					createVoucherOrder(voucherOrder);
					// 4.确认消息 XACK
					stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
				} catch (Exception e) {
					log.error("处理订单异常", e);
					handlePendingList();
				}
			}
		}
		
		private void handlePendingList() {
			while (true) {
				try {
					// 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
					List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
							Consumer.from("g1", "c1"),
							StreamReadOptions.empty().count(1),
							StreamOffset.create("stream.orders", ReadOffset.from("0"))
					);
					// 2.判断订单信息是否为空
					if (list == null || list.isEmpty()) {
						// 如果为null，说明没有异常消息，结束循环
						break;
					}
					// 解析数据
					MapRecord<String, Object, Object> record = list.get(0);
					Map<Object, Object> value = record.getValue();
					VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
					// 3.创建订单
					createVoucherOrder(voucherOrder);
					// 4.确认消息 XACK
					stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
				} catch (Exception e) {
					log.error("处理订单异常", e);
				}
			}
		}
		
		//阻塞队列
		/*private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
		@Override
		public void run() {
			while (true) {
				try {
					// %1.获取队列中的订单信息
					VoucherOrder voucherOrder = orderTasks.take();
					// %2.创建订单
					handleVoucherOrder(voucherOrder);
				} catch (Exception e) {
					log.error("处理订单异常", e);
				}
			}
		}
		
		private void handleVoucherOrder(VoucherOrder voucherOrder) {
			// %1.获取用户
			// 多线程获取用户id
			Long userId = voucherOrder.getUserId();
			// %2.创建锁对象
			RLock redisLock = redissonClient.getLock("lock:order:" + userId);
			// %3.尝试获取锁
			boolean isLock = redisLock.tryLock();
			// %4.判断是否获得锁成功
			if (!isLock) {
				// 获取锁失败，直接返回失败或者重试
				log.error("不允许重复下单！");
				return;
			}
			try {
				//注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
				proxy.createVoucherOrder(voucherOrder);
			} finally {
				// 释放锁
				redisLock.unlock();
			}
		}
		*/

	}

	/**
	 * @Description 秒杀下单seckillVoucher
	 * @Param [voucherId]
	 * @return com.hmdp.dto.Result
	 */
	//版本4:多线程使用Redis以及消息队列
	@Override
	public Result seckillVoucher(Long voucherId) {
		Long userId = UserHolder.getUser().getId();
		long orderId = redisIdWorker.nextId("order");
		// 1.执行lua脚本
		Long result = stringRedisTemplate.execute(
				SECKILL_SCRIPT,
				Collections.emptyList(),
				voucherId.toString(), userId.toString(), String.valueOf(orderId)
		);
		int r = result.intValue();
		// 2.判断结果是否为0
		if (r != 0) {
			// 2.1.不为0 ，代表没有购买资格
			return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
		}
		//x3.获取代理对象
		//proxy = (IVoucherOrderService) AopContext.currentProxy();
		// 3.返回订单id
		return Result.ok(orderId);
	}
	
	//版本3:多线程使用Redis以及阻塞队列
	/*//代理对象(线程的)
	private IVoucherOrderService proxy;
	@Override
	public Result seckillVoucher(Long voucherId) {
		Long userId = UserHolder.getUser().getId();
		long orderId = redisIdWorker.nextId("order");
		// 1.执行lua脚本
		Long result = stringRedisTemplate.execute(
				SECKILL_SCRIPT,
				Collections.emptyList(),
				voucherId.toString(), userId.toString(), String.valueOf(orderId)
		);
		int r = result.intValue();
		// 2.判断结果是否为0
		if (r != 0) {
			// 2.1.不为0 ，代表没有购买资格
			return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
		}
		//放入lua中解决
		//VoucherOrder voucherOrder = new VoucherOrder();
		// %2.3.订单id
		//long orderId = redisIdWorker.nextId("order");
		//voucherOrder.setId(orderId);
		// %2.4.用户id
		//voucherOrder.setUserId(userId);
		// %2.5.代金券id
		//voucherOrder.setVoucherId(voucherId);
		// %2.6.放入阻塞队列
		//orderTasks.add(voucherOrder);
		//3.获取代理对象
		proxy = (IVoucherOrderService) AopContext.currentProxy();
		//4.返回订单id
		return Result.ok(orderId);
	}*/

	//版本2:单线程使用Redis以及阻塞队列
	/*@Override
	public Result seckillVoucher(Long voucherId) {
		//单线程获取用户id
		Long userId = UserHolder.getUser().getId();
		// 1.执行lua脚本
		Long result = stringRedisTemplate.execute(
				SECKILL_SCRIPT,
				Collections.emptyList(),//传入key空集合(不要传null)
				voucherId.toString(), userId.toString()
		);
		int r = result.intValue();
		// 2.判断结果是否为0
		if (r != 0) {
			// 2.1.不为0 ，代表没有购买资格
			return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
		}
		// 2.2.为0 ，有购买资格，把下单信息保存到阻塞队列
		VoucherOrder voucherOrder = new VoucherOrder();
		// 2.3.创建订单id
		long orderId = redisIdWorker.nextId("order");
		voucherOrder.setId(orderId);
		// 2.4.用户id
		voucherOrder.setUserId(userId);
		// 2.5.代金券id
		voucherOrder.setVoucherId(voucherId);
		// 2.6.放入阻塞队列
		orderTasks.add(voucherOrder);
		
		// 3.返回订单id
		return Result.ok(orderId);
	}*/
	
	//版本1:使用数据库判定
/*	@Override
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
	}*/
	
	/**
	 * @Description 创建优惠券订单(一人一单)
	 * @Param [voucherOrder]
	 * !使用异步操作,不需要向前端返回数据
	 * @return void
	 */
	//版本2:代理方式创建优惠券订单(一人一单)
	@Override
	public void createVoucherOrder(VoucherOrder voucherOrder) {
		// 一人一单逻辑
		// 用户id
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
			
			// 7.创建订单(不用再new创建订单)
			save(voucherOrder);
			//也不需要返回数据了
			//return Result.ok(orderId);
		} finally {
			// 释放锁
			redisLock.unlock();
		}
	}
	//版本1.5:未加锁
	/*		@Transactional
		public void createVoucherOrder(VoucherOrder voucherOrder) {
			Long userId = voucherOrder.getUserId();
			// 5.1.查询订单
			int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
			// 5.2.判断是否存在
			if (count > 0) {
				// 用户已经购买过了
				log.error("用户已经购买过了");
				return;
			}
			
			// 6.扣减库存
			boolean success = seckillVoucherService.update()
					.setSql("stock = stock - 1") // set stock = stock - 1
					.eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
					.update();
			if (!success) {
				// 扣减失败
				log.error("库存不足");
				return;
			}
			save(voucherOrder);
			
		}*/
	//版本1:单线程创建优惠券订单(一人一单)
/*	@Override
	@Transactional
	public Result createVoucherOrder(Long voucherId) {
		// %5.一人一单逻辑
		// %5.1.用户id
		Long userId = UserHolder.getUser().getId();
		
		int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
		// %5.2.判断是否存在
		if (count > 0) {
			// 用户已经购买过了
			return Result.fail("用户已经购买过一次！");
		}
		
		//log.info("版本号为:" + voucher.getVersion());
		//%6，扣减库存
		//-扣减库存存入数据库
		//乐观锁:更新时才判断是否有其他线程在修改
		boolean success = seckillVoucherService.update()
				.setSql("stock= stock -1")
				.eq("voucher_id", voucherId).gt("stock", 0).update(); //where id = ? and stock > 0
			*//*boolean success = seckillVoucherService.update()
			.setSql("stock= stock -1")// , version = version +1"
			.eq("voucher_id", voucherId)
			//.eq("version", voucher.getVersion())
			.update();//返回boolean*//*
	
			*//*LambdaUpdateWrapper<SeckillVoucher> updateWrapper = new LambdaUpdateWrapper<>();
			updateWrapper.setSql("stock= stock -1")// , version = version +1"
					.eq(SeckillVoucher::getVoucherId, voucherId)
					.gt(SeckillVoucher::getStock,0)
					;//返回boolean
			boolean success = seckillVoucherService.update(voucher, updateWrapper);*//*
				//log.info("扣减后,版本号为:" + voucher.getVersion());
		if (!success) {
			//扣减失败
			return Result.fail("扣减失败,库存可能不足！");
		}
		//%7.创建订单
		VoucherOrder voucherOrder = new VoucherOrder();
		// %7.1.订单id
		long orderId = redisIdWorker.nextId("order");
		voucherOrder.setId(orderId);
		// %7.2.用户id
		userId = UserHolder.getUser().getId();
		voucherOrder.setUserId(userId);
		// %7.3.代金券id
		voucherOrder.setVoucherId(voucherId);
		//-新增订单存入数据库
		save(voucherOrder);
		
		return Result.ok(orderId);
		
	}*/
}
