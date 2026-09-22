package com.dongqh.cartservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dongqh.cartservice.domain.dto.CartFormDTO;
import com.dongqh.cartservice.domain.po.Cart;
import com.dongqh.cartservice.domain.vo.CartVO;
import com.dongqh.cartservice.mapper.CartMapper;
import com.dongqh.cartservice.service.ICartService;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.ItemDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

    private final ItemClient itemClient;

    @Override
    public void addItem2Cart(CartFormDTO cartFormDTO) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();

        // 2.判断是否已经存在
        if(checkItemExists(cartFormDTO.getItemId(), userId)){
            // 2.1.存在，则更新数量
            baseMapper.updateNum(cartFormDTO.getItemId(), userId);
            return;
        }
        // 2.2.不存在，判断是否超过购物车数量
        checkCartsFull(userId);

        // 3.新增购物车条目
        // 3.1.转换PO
        Cart cart = BeanUtils.copyBean(cartFormDTO, Cart.class);
        // 3.2.保存当前用户
        cart.setUserId(userId);
        // 3.3.保存到数据库
        save(cart);
    }

    @Override
    public List<CartVO> queryMyCarts() {
        // 1.查询我的购物车列表
        List<Cart> carts = query().eq("user_id", UserContext.getUser()).list();
        if (CollUtils.isEmpty(carts)) {
            return CollUtils.emptyList();
        }

        // 2.转换VO
        List<CartVO> vos = BeanUtils.copyList(carts, CartVO.class);

        // 3.处理VO中的商品信息
        handleCartItems(vos);

        // 4.返回
        return vos;
    }

    private void handleCartItems(List<CartVO> vos) {
        //  接入 OpenFeign 后，批量调用 item-service 查询商品最新价格、状态和库存。
        Set<Long> itemIds = vos.stream().map(CartVO::getItemId).collect(Collectors.toSet());
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);

        if(CollUtil.isEmpty(items)){ return;}

        //转为id 到 item的map
        Map<Long, ItemDTO> itemMap = items.stream().collect(Collectors.toMap(ItemDTO::getId, item -> item));

        for (CartVO cart : vos) {
            ItemDTO item = itemMap.get(cart.getItemId());
            if(item == null){ continue;}
            cart.setNewPrice(item.getPrice());
            cart.setStatus(item.getStatus());
            cart.setStock(item.getStock());
        }
    }

    @Override
    public void removeByItemIds(Collection<Long> itemIds) {
        // 1.构建删除条件，userId和itemId
        QueryWrapper<Cart> queryWrapper = new QueryWrapper<Cart>();
        queryWrapper
                .eq("user_id", UserContext.getUser())
                .in("item_id", itemIds);
        // 2.删除
        remove(queryWrapper);
    }

    @Override
    public void removeOrderedItems(Long userId, Collection<Long> itemIds, LocalDateTime orderedAt) {
        if (userId == null || itemIds == null || itemIds.isEmpty() || orderedAt == null) return;
        remove(new QueryWrapper<Cart>()
                .eq("user_id", userId)
                .in("item_id", itemIds)
                .le("update_time", orderedAt));
    }

    private void checkCartsFull(Long userId) {
        long count = query().eq("user_id", userId).count();
        if (count >= 10) {
            throw new BizIllegalException(StrUtil.format("用户购物车课程不能超过{}", 10));
        }
    }

    private boolean checkItemExists(Long itemId, Long userId) {
        long count = query()
                .eq("user_id", userId)
                .eq("item_id", itemId)
                .count();
        return count > 0;
    }

}
