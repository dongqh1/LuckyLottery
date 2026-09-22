package com.dongqh.tradeservice.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dongqh.tradeservice.domain.po.OrderDetail;
import com.dongqh.tradeservice.mapper.OrderDetailMapper;
import com.dongqh.tradeservice.service.IOrderDetailService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
public class OrderDetailServiceImpl extends ServiceImpl<OrderDetailMapper, OrderDetail> implements IOrderDetailService {

}
