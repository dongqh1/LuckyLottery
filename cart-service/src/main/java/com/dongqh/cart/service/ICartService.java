package com.dongqh.cart.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dongqh.cart.domain.dto.CartFormDTO;
import com.dongqh.cart.domain.po.Cart;
import com.dongqh.cart.domain.vo.CartVO;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单详情表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
public interface ICartService extends IService<Cart> {

    void addItem2Cart(CartFormDTO cartFormDTO);

    List<CartVO> queryMyCarts();

    void removeByItemIds(Collection<Long> itemIds);

    void removeOrderedItems(Long userId, Collection<Long> itemIds, LocalDateTime orderedAt);
}
