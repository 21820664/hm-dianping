package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
	
	/**
	 * @Description 秒杀下单
	 * @Param [voucherId]
	 * @return com.hmdp.dto.Result
	 */
	Result seckillVoucher(Long voucherId);
	
	/**
	 * @Description 创建优惠券订单
	 * @Param [voucherId]
	 * @return com.hmdp.dto.Result
	 */
	Result createVoucherOrder(Long voucherId);
}
