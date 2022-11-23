package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  优惠券订单服务
 * </p>
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
	 * @Param [voucherOrder]
	 * @return void
	 * //@Param [voucherId]
	 * //@return com.hmdp.dto.Result
	 */
	void createVoucherOrder(VoucherOrder voucherOrder);
}
