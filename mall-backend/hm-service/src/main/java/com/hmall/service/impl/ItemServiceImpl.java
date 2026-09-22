package com.hmall.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.domain.dto.ItemDTO;
import com.hmall.domain.dto.OrderDetailDTO;
import com.hmall.domain.po.Item;
import com.hmall.mapper.ItemMapper;
import com.hmall.service.IItemService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Service
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    @Override
    public void deductStock(List<OrderDetailDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BizIllegalException("扣减库存明细不能为空！");
        }
        for (OrderDetailDTO item : items) {
            if (item == null || item.getItemId() == null || item.getNum() == null || item.getNum() <= 0) {
                throw new BizIllegalException("扣减库存参数不合法！");
            }
            if (baseMapper.updateStock(item) != 1) {
                throw new BizIllegalException("库存不足！");
            }
        }
    }

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        return BeanUtils.copyList(listByIds(ids), ItemDTO.class);
    }
}
