package com.dongqh.cart;

import com.dongqh.cart.domain.dto.CartFormDTO;
import com.dongqh.cart.domain.vo.CartVO;
import com.dongqh.cart.service.ICartService;
import com.dongqh.hmapi.client.ItemClient;
import com.dongqh.hmapi.dto.ItemDTO;
import com.hmall.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "seata.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:cart_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class CartServiceApplicationTests {

    @Autowired
    private ICartService cartService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ItemClient itemClient;

    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void queryMyCartsUsesTheItemServiceSnapshot() {
        UserContext.setUser(7L);
        CartFormDTO form = new CartFormDTO();
        form.setItemId(1001L);
        form.setName("临时商品");
        form.setPrice(2999);
        form.setImage("item.png");

        ItemDTO item = new ItemDTO();
        item.setId(1001L);
        item.setPrice(2999);
        item.setStatus(1);
        item.setStock(10);
        when(itemClient.queryItemByIds(anyCollection())).thenReturn(List.of(item));

        cartService.addItem2Cart(form);
        List<CartVO> carts = cartService.queryMyCarts();

        assertEquals(1, carts.size());
        assertEquals(2999, carts.get(0).getNewPrice());
        assertEquals(1, carts.get(0).getStatus());
        assertEquals(10, carts.get(0).getStock());
    }

    @Test
    @Transactional
    void missingUserContextDoesNotFallBackToUserOne() {
        UserContext.setUser(1L);
        CartFormDTO form = new CartFormDTO();
        form.setItemId(1002L);
        form.setName("用户一商品");
        form.setPrice(1999);
        form.setImage("item-1.png");
        cartService.addItem2Cart(form);

        UserContext.removeUser();

        assertEquals(0, cartService.queryMyCarts().size());
    }

    @Test
    @Transactional
    void asynchronousCleanupPreservesItemsUpdatedAfterCheckout() {
        var checkoutAt = java.time.LocalDateTime.of(2026, 9, 17, 12, 0);
        jdbc.update("INSERT INTO cart(user_id,item_id,num,name,price,update_time) VALUES (7,1001,1,'old',100,?)",
                checkoutAt.minusSeconds(1));
        jdbc.update("INSERT INTO cart(user_id,item_id,num,name,price,update_time) VALUES (7,1002,1,'readded',100,?)",
                checkoutAt.plusSeconds(1));
        jdbc.update("INSERT INTO cart(user_id,item_id,num,name,price,update_time) VALUES (8,1001,1,'other-user',100,?)",
                checkoutAt.minusSeconds(1));

        cartService.removeOrderedItems(7L, List.of(1001L, 1002L), checkoutAt);

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cart WHERE user_id=7 AND item_id=1001", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cart WHERE user_id=7 AND item_id=1002", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cart WHERE user_id=8 AND item_id=1001", Integer.class));
    }

}
