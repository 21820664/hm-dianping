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
import org.springframework.stereotype.Service;

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
		//log.info("版本号为:" + voucher.getVersion());
		//5，扣减库存
		//-扣减库存存入数据库
		//乐观锁:更新时才判断是否有其他线程在修改
		boolean success = seckillVoucherService.update()
				.setSql("stock= stock -1")
				.eq("voucher_id", voucherId).gt("stock",0).update(); //where id = ? and stock > 0
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
		//6.创建订单
		VoucherOrder voucherOrder = new VoucherOrder();
		// 6.1.订单id
		long orderId = redisIdWorker.nextId("order");
		voucherOrder.setId(orderId);
		// 6.2.用户id
		Long userId = UserHolder.getUser().getId();
		voucherOrder.setUserId(userId);
		// 6.3.代金券id
		voucherOrder.setVoucherId(voucherId);
		//-新增订单存入数据库
		save(voucherOrder);
		
		return Result.ok(orderId);
		
	}
}
