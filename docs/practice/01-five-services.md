# 第一组：五个微服务重点代码填空与答案

本组依据当前仓库 `40d2c61` 的业务代码抽取。保留业务条件和调用方式，增加中文步骤注释；少数方法去掉日志或采用简短变量名，会在题目中说明。Q10 的配置只是指定节点的练习片段，不是一份可以直接覆盖原文件的完整配置。

从 **Q01** 开始就能看到完整练习形式。占位符 `__Q01_01__` 表示你要填写的内容；答案集中在全部题目之后。

## 题目目录

| 题号 | 服务或模块 | 该部分完成的工作 | 题目 | 答案 |
| --- | --- | --- | --- | --- |
| Q01 | cart | 查看自己的购物车，批量补充商品最新信息 | [开始](#q01) | [答案](#a01) |
| Q02 | cart | 添加商品；按商品 ID 批量删除自己的条目 | [开始](#q02) | [答案](#a02) |
| Q03 | item | 商品的新增、查询、修改、删除 | [开始](#q03) | [答案](#a03) |
| Q04 | item + hm-common | 商品分页查询与分页插件配置 | [开始](#q04) | [答案](#a04) |
| Q05 | user | 校验登录信息，生成 JWT | [开始](#q05) | [答案](#a05) |
| Q06 | item | 条件扣库存，避免并发时扣成负数 | [开始](#q06) | [答案](#a06) |
| Q07 | trade | 用数据库行锁处理重复支付成功回调 | [开始](#q07) | [答案](#a07) |
| Q08 | pay | 条件更新支付状态；在事务中写 Outbox | [开始](#q08) | [答案](#a08) |
| Q09 | cart + hm-api | 在启动类上启用 Mapper 和 Feign | [开始](#q09) | [答案](#a09) |
| Q10 | 公共配置 + user | Nacos、数据库、RabbitMQ、Seata、JWT 配置 | [开始](#q10) | [答案](#a10) |
| Q11 | lottery，补充题 | Redis Lua 原子预占抽奖次数并处理重试 | [开始](#q11) | [答案](#a11) |
| Q12 | lottery，补充题 | Redisson 的连接配置、加锁、释放锁 | [开始](#q12) | [答案](#a12) |

## 读代码前的几个词

- **DTO**：请求或服务间传输的数据；**PO**：数据库实体；**VO**：返回给页面的数据。
- **`UserContext`**：当前请求的用户上下文。本组沿用原方法的登录前置条件，不从请求正文接收用户 ID。
- **`baseMapper`、`save`、`query`、`lambdaUpdate`**：原 Service 继承 MyBatis-Plus 的 `ServiceImpl` 后可使用的数据库操作。
- **幂等**：同一个业务动作重试时，不重复产生业务效果，例如同一支付回调不会再次改变支付时间。
- **Outbox**：数据库中的待发送事件记录。写入 Outbox 和消息已经被 RabbitMQ 接收是两个不同步骤。

题目的导入采用原文件中的导入；特别是 Q01 的 `ItemDTO` 来自 `com.dongqh.hmapi.dto`，Q03、Q04 的 `ItemDTO` 来自 `com.dongqh.itemservice.domain.dto`。

<a id="q01"></a>

## Q01：查看我的购物车，并补充商品最新信息

**该部分是完成什么工作的：** 页面查看购物车时，先查询当前用户的购物车记录，再一次性调用商品服务，补充最新价格、上下架状态和库存。

**原文件路径：** [CartServiceImpl.java](E:/ANotes/software/hmall-lottery/cart-service/src/main/java/com/dongqh/cartservice/service/impl/CartServiceImpl.java)，定位 `queryMyCarts`、`handleCartItems`。入口是 [CartController.java](E:/ANotes/software/hmall-lottery/cart-service/src/main/java/com/dongqh/cartservice/controller/CartController.java) 的 `GET /carts`。

**主题组件：** MyBatis-Plus 查询购物车；OpenFeign 批量调用 `item-service`；Map 帮助按商品 ID 找到对应的商品信息。

**代码范围与上下文：** 两个完整方法。类已继承 `ServiceImpl<CartMapper, Cart>`，有成员 `private final ItemClient itemClient`。`BeanUtils`、`CollUtils` 来自 `com.hmall.common.utils`，`CollUtil` 来自 Hutool；使用 `List`、`Set`、`Map`、`Collectors`。`CartVO` 已有 `itemId`、`newPrice`、`status`、`stock` 等属性。

**练习代码：**

```java
public List<CartVO> queryMyCarts() {
    // 1：只查询当前登录用户的记录，不能查出其他人的购物车。
    List<Cart> carts = query().eq("user_id", __Q01_01__).list();

    // 2：购物车为空时直接返回空列表，减少不必要的远程调用。
    if (CollUtils.isEmpty(carts)) {
        return __Q01_02__;
    }

    // 3：把数据库实体转换为页面使用的 VO 列表。
    List<CartVO> vos = __Q01_03__;

    // 4：调用下面的辅助方法，填充商品最新信息。
    __Q01_04__;
    return vos;
}

private void handleCartItems(List<CartVO> vos) {
    // 5：从购物车提取商品 ID，使用 Set 去重。
    Set<Long> itemIds = vos.stream()
            .map(__Q01_05__)
            .collect(Collectors.toSet());

    // 6：批量查询商品，一次请求传入所有 ID。
    List<ItemDTO> items = __Q01_06__;
    if (CollUtil.isEmpty(items)) {
        return;
    }

    // 7：建立“商品 ID -> 商品信息”的映射，便于逐条补充购物车。
    Map<Long, ItemDTO> itemMap = items.stream()
            .collect(Collectors.toMap(__Q01_07__, item -> item));

    for (CartVO cart : vos) {
        // 8：按当前条目的商品 ID 查找；商品未返回时跳过。
        ItemDTO item = itemMap.get(__Q01_08__);
        if (item == null) {
            continue;
        }

        // 9：填充最新价格、状态、库存，保留原来的购物车条目数据。
        cart.setNewPrice(__Q01_09__);
        cart.setStatus(__Q01_10__);
        cart.setStock(__Q01_11__);
    }
}
```

**自查场景：** 用户 A 有两条记录、用户 B 有一条，A 应只拿到自己的两条；A 的购物车为空时不调用商品服务；商品最新价格应写入 `newPrice`。如果某商品未返回，当前源码会保留该条目原值，不会自动把它标为失效。

<a id="q02"></a>

## Q02：添加商品，批量删除自己的购物车条目

**该部分是完成什么工作的：** 同一用户再次添加已有商品时，让原条目数量增加 1；添加新商品时创建条目。批量删除时同时限制当前用户和商品 ID。

**原文件路径：** [CartServiceImpl.java](E:/ANotes/software/hmall-lottery/cart-service/src/main/java/com/dongqh/cartservice/service/impl/CartServiceImpl.java)，定位 `addItem2Cart`、`removeByItemIds`。数量更新 SQL 在 [CartMapper.java](E:/ANotes/software/hmall-lottery/cart-service/src/main/java/com/dongqh/cartservice/mapper/CartMapper.java)。

**主题组件：** MyBatis-Plus 的保存和条件删除；自定义 Mapper 的原子 `num = num + 1` 更新。

**代码范围与上下文：** 两个完整方法。已有 `checkItemExists(itemId, userId)` 和 `checkCartsFull(userId)`；后者在条目数达到 10 时抛出业务异常。使用 `QueryWrapper<Cart>`、`Collection<Long>`。此题复现源码顺序，检查后新增的整体流程并没有因此成为并发原子操作。

**练习代码：**

```java
public void addItem2Cart(CartFormDTO form) {
    // 1：从当前请求上下文取用户，不信任前端传来的用户 ID。
    Long userId = __Q02_01__;

    // 2：商品已经在当前用户的购物车里时，更新数量并结束。
    if (__Q02_02__) {
        __Q02_03__;
        return;
    }

    // 3：新条目才需要检查购物车条目数上限。
    __Q02_04__;

    // 4：将请求转换为数据库实体，明确绑定当前用户。
    Cart cart = BeanUtils.copyBean(form, Cart.class);
    __Q02_05__;

    // 5：保存新条目。
    __Q02_06__;
}

public void removeByItemIds(Collection<Long> itemIds) {
    // 6：这里的 itemIds 是商品 ID，不是购物车记录的主键 ID。
    QueryWrapper<Cart> wrapper = new QueryWrapper<Cart>()
            .eq("user_id", __Q02_07__)
            .in("item_id", __Q02_08__);

    // 7：根据两个条件删除匹配的购物车条目。
    __Q02_09__;
}
```

**自查场景：** 已存在商品只增数量，不再新增一条；第 11 种商品被上限检查拒绝；A 删除商品 1001 时，B 的同商品条目仍保留。此批量删除方法沿用源码的非空 ID 集合调用前提。

<a id="q03"></a>

## Q03：商品增删改查

**该部分是完成什么工作的：** 管理商品信息，完成新增、按 ID 查询、修改资料和删除。商品上下架由另一个状态接口管理，普通资料修改不会修改状态。

**原文件路径：** [ItemController.java](E:/ANotes/software/hmall-lottery/item-service/src/main/java/com/dongqh/itemservice/controller/ItemController.java)，定位下面四个方法。

**主题组件：** Spring MVC 将请求映射到方法；MyBatis-Plus 提供 `save`、`getById`、`updateById`、`removeById`；`BeanUtils` 在 DTO 和 PO 之间复制属性。

**代码范围与上下文：** 四个完整方法，省去 Swagger 注解。原类已有 `@RestController`、`@RequestMapping("/items")` 和成员 `private final IItemService itemService`。

**练习代码：**

```java
@PostMapping
public void saveItem(@RequestBody ItemDTO item) {
    // 1：DTO 转为商品实体后新增。
    itemService.save(__Q03_01__);
}

@GetMapping("{id}")
public ItemDTO queryItemById(@PathVariable("id") Long id) {
    // 2：按主键查询实体，转换成 DTO 返回。
    return BeanUtils.copyBean(__Q03_02__, ItemDTO.class);
}

@PutMapping
public void updateItem(@RequestBody ItemDTO item) {
    // 3：普通资料修改不能顺便修改上下架状态。
    __Q03_03__;

    // 4：按 DTO 中的 ID 更新实体。
    itemService.updateById(__Q03_04__);
}

@DeleteMapping("{id}")
public void deleteItemById(@PathVariable("id") Long id) {
    // 5：按主键删除商品。
    __Q03_05__;
}
```

**自查场景：** 新增后按 ID 能查到商品；更新时即使传入了 `status`，也不能通过此方法改变上下架状态。此处依赖当前实体配置中更新时忽略 null 字段的策略；这些方法只展示当前 Controller 的 CRUD 调用，不额外实现商品不存在时的响应规范。

<a id="q04"></a>

## Q04：商品分页查询，以及分页插件

**该部分是完成什么工作的：** 按页返回商品列表，并附带总记录数和总页数；默认按更新时间倒序。

**原文件路径：** [ItemController.java](E:/ANotes/software/hmall-lottery/item-service/src/main/java/com/dongqh/itemservice/controller/ItemController.java)，定位 `queryItemByPage`；[MyBatisConfig.java](E:/ANotes/software/hmall-lottery/mall-backend/hm-common/src/main/java/com/hmall/common/config/MyBatisConfig.java)，定位 `mybatisPlusInterceptor`。相关类是 [PageQuery.java](E:/ANotes/software/hmall-lottery/mall-backend/hm-common/src/main/java/com/hmall/common/domain/PageQuery.java) 和 [PageDTO.java](E:/ANotes/software/hmall-lottery/mall-backend/hm-common/src/main/java/com/hmall/common/domain/PageDTO.java)。

**主题组件：** `PageQuery` 接收分页参数；MyBatis-Plus 分页拦截器处理分页 SQL；`PageDTO` 将实体记录转为接口需要的 DTO。

**代码范围与上下文：** 一个 Controller 方法和一个完整配置类；Controller 方法省去原来的调试请求头与输出。`PageDTO` 的字段是 `total`、`pages`、`list`。`query.toMpPage("update_time", false)` 只在没有指定 `sortBy` 时设置默认排序；本题自查使用未指定排序的请求。

**练习代码：**

```java
@GetMapping("/page")
public PageDTO<ItemDTO> queryItemByPage(PageQuery query) {
    // 1：构造分页参数，默认按 update_time 倒序，并查询数据库。
    Page<Item> result = itemService.page(__Q04_01__);

    // 2：保留总数和总页数，将记录转为 ItemDTO。
    return __Q04_02__;
}
```

```java
@Configuration
@ConditionalOnClass({MybatisPlusInterceptor.class, BaseMapper.class})
public class MyBatisConfig {
    // 3：把拦截器注册为 Bean；已有同类型 Bean 时避免重复创建。
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 4：使用 MySQL 类型的分页拦截器。
        PaginationInnerInterceptor pagination = __Q04_03__;

        // 5：将单页上限设为 1000 条，然后装入总拦截器。
        pagination.setMaxLimit(__Q04_04__);
        __Q04_05__;
        return interceptor;
    }
}
```

**自查场景：** 21 条商品、每页 10 条时，应返回总数 21、总页数 3；第 3 页只有 1 条；没有客户端排序参数时使用默认更新时间倒序。相关导入均可在原配置类中找到。

<a id="q05"></a>

## Q05：用户登录，校验密码并生成 JWT

**该部分是完成什么工作的：** 根据用户名找到用户，拒绝被冻结的账号，验证输入密码，生成令牌并返回登录结果。

**原文件路径：** [UserServiceImpl.java](E:/ANotes/software/hmall-lottery/user-service/src/main/java/com/dongqh/userservice/service/impl/UserServiceImpl.java)，定位 `login`。相关配置见 Q10。

**主题组件：** `PasswordEncoder` 校验输入密码与数据库密码摘要；项目的 `JwtTool` 根据用户 ID、角色、有效期生成令牌；`JwtProperties` 提供有效期。

**代码范围与上下文：** 完整登录方法。原类已有 `passwordEncoder`、`jwtTool`、`jwtProperties` 三个成员；相关类型为 `User`、`UserStatus`、`LoginFormDTO`、`UserLoginVO`、Spring 的 `Assert` 和项目异常。该方法实际只按用户名查询，原注释提及手机号，但这里没有手机号条件。

**练习代码：**

```java
public UserLoginVO login(LoginFormDTO form) {
    // 1：读取表单中的用户名和明文密码。
    String username = form.getUsername();
    String password = form.getPassword();

    // 2：按用户名查询唯一用户；没有此用户则结束登录。
    User user = lambdaQuery().eq(__Q05_01__, username).one();
    Assert.notNull(user, "用户名错误");

    // 3：被冻结的用户不能登录。
    if (__Q05_02__) {
        throw new ForbiddenException("用户被冻结");
    }

    // 4：使用编码器验证密码，不能直接比较输入与数据库摘要。
    if (!__Q05_03__) {
        throw new BadRequestException("用户名或密码错误");
    }

    // 5：令牌写入用户身份和角色，并使用配置中的有效期。
    String token = __Q05_04__;

    // 6：返回页面需要的登录信息，不返回密码摘要。
    UserLoginVO vo = new UserLoginVO();
    vo.setUserId(user.getId());
    vo.setUsername(user.getUsername());
    vo.setBalance(user.getBalance());
    vo.setToken(__Q05_05__);
    return vo;
}
```

**自查场景：** 密码正确且账号正常时获得 token；密码错误时不能生成 token；冻结账号即使密码正确也被拒绝。密码编码器的实现由 Q10 的 Bean 提供。

<a id="q06"></a>

## Q06：用条件 UPDATE 扣库存

**该部分是完成什么工作的：** 订单购买商品时扣减库存，同时拒绝非法数量和库存不足。并发请求不能仅凭先前读到的库存做无条件更新。

**原文件路径：** [ItemMapper.java](E:/ANotes/software/hmall-lottery/item-service/src/main/java/com/dongqh/itemservice/mapper/ItemMapper.java)，定位 `updateStock`；[ItemServiceImpl.java](E:/ANotes/software/hmall-lottery/item-service/src/main/java/com/dongqh/itemservice/service/impl/ItemServiceImpl.java)，定位 `deductStock`。

**主题组件：** MySQL 在一条 SQL 中判断余额式条件并修改库存；MyBatis 用 `#{}` 绑定参数；Service 检查受影响行数是否为 1。这里使用数据库条件更新，不是 Redis 锁。

**代码范围与上下文：** Mapper 方法声明和完整 Service 方法。`OrderDetailDTO` 来自 `com.dongqh.hmapi.dto`，包含 `itemId` 和 `num`；原 Service 已有 `baseMapper`。

**练习代码：**

```java
// 1：扣减数量为正，且当前库存足够时，才更新指定商品。
@Update("UPDATE item SET stock = stock - #{num} " +
        "WHERE id = #{itemId} AND __Q06_01__ AND __Q06_02__")
int updateStock(OrderDetailDTO orderDetail);
```

```java
public void deductStock(List<OrderDetailDTO> items) {
    // 2：空明细不能执行库存扣减。
    if (items == null || items.isEmpty()) {
        throw new BizIllegalException("扣减库存明细不能为空！");
    }

    for (OrderDetailDTO item : items) {
        // 3：每条明细都必须有商品 ID 和正整数数量。
        if (item == null || item.getItemId() == null
                || item.getNum() == null || __Q06_03__) {
            throw new BizIllegalException("扣减库存参数不合法！");
        }

        // 4：检查 SQL 实际更新行数；没有更新成功就抛出异常。
        if (__Q06_04__ != 1) {
            throw new BizIllegalException("库存不足！");
        }
    }
}
```

**自查场景：** 库存 5、两个请求各扣 4，只有一个成功，最终库存 1；数量为 0 或负数被拒绝。单条 SQL 的安全扣减不代表整个多商品循环自动具有事务：当前方法未声明本地 `@Transactional`，订单创建链路依靠现有 Seata 全局事务协调，脱离该链路调用时要另行考虑整体回滚。

<a id="q07"></a>

## Q07：支付成功回调的数据库行锁与幂等

**该部分是完成什么工作的：** 支付成功消息可能重复到达。交易服务应校验订单归属和金额，将待支付订单标为已支付；同一订单已支付时直接返回。

**原文件路径：** [OrderServiceImpl.java](E:/ANotes/software/hmall-lottery/trade-service/src/main/java/com/dongqh/tradeservice/service/impl/OrderServiceImpl.java)，定位四参数的 `markOrderPaySuccess`；[OrderMapper.java](E:/ANotes/software/hmall-lottery/trade-service/src/main/java/com/dongqh/tradeservice/mapper/OrderMapper.java)，定位 `selectByIdForUpdate`。

**主题组件：** Spring `@Transactional` 保持查询和更新处于同一本地事务；MySQL `FOR UPDATE` 锁定订单记录；状态判断处理重复回调。订单状态 1 表示待支付，2 表示已支付。

**代码范围与上下文：** 完整 Mapper 方法声明和完整 Service 方法。Mapper 注解沿用原文件的全限定名；Service 的 `Transactional`、`Order`、`LocalDateTime`、`BadRequestException` 等导入沿用原文件。

**练习代码：**

```java
// 1：按订单 ID 查询，并在当前事务内对记录加写锁。
@org.apache.ibatis.annotations.Select("SELECT * FROM `order` WHERE id = #{id} __Q07_01__")
Order selectByIdForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
```

```java
// 2：让行锁覆盖本方法中的查询、校验和更新过程。
__Q07_02__
public void markOrderPaySuccess(Long orderId, Long userId,
                                Integer amount, LocalDateTime paidAt) {
    // 3：拒绝缺失参数和非法金额。
    if (orderId == null || userId == null || amount == null
            || amount <= 0 || paidAt == null) {
        throw new BadRequestException("订单支付参数不合法");
    }

    // 4：查询并锁定订单；校验消息对应的用户和金额。
    Order order = __Q07_03__;
    if (order == null || !userId.equals(order.getUserId())
            || !amount.equals(order.getTotalFee())) {
        throw new BadRequestException("订单状态或金额已发生变化");
    }

    // 5：合法的重复回调不再次修改支付时间。
    if (__Q07_04__) {
        return;
    }

    // 6：其余状态中，只有待支付订单可以进入已支付状态。
    if (!__Q07_05__) {
        throw new BadRequestException("订单状态或金额已发生变化");
    }

    // 7：写入已支付状态和消息携带的支付时间。
    __Q07_06__;
    order.setPayTime(paidAt);

    // 8：必须确实更新一行，否则抛出异常。
    if (__Q07_07__ != 1) {
        throw new BadRequestException("订单状态或金额已发生变化");
    }
}
```

**自查场景：** 正确回调将 1 改成 2；重复回调不覆盖第一次的支付时间；错误用户、错误金额、已取消订单被拒绝。行锁需在事务内使用，方法由 Spring 管理的 Service 代理调用时，注解才能提供这里所需的事务边界。

<a id="q08"></a>

## Q08：支付状态条件更新，以及事务内的 Outbox

**该部分是完成什么工作的：** 支付服务仅允许尚未成功的支付单进入成功状态；同一业务事务还保存“支付成功”事件，交给现有 relay 后续投递。

**原文件路径：** [PayOrderServiceImpl.java](E:/ANotes/software/hmall-lottery/pay-service/src/main/java/com/dongqh/payservice/service/impl/PayOrderServiceImpl.java)，定位 `markPayOrderSuccess`、调用方 `tryPayOrderByBalance`；[PayOutboxService.java](E:/ANotes/software/hmall-lottery/pay-service/src/main/java/com/dongqh/payservice/messaging/PayOutboxService.java)，定位 `append`。

**主题组件：** MyBatis-Plus 条件更新实现比较后更新；Spring 的 `Propagation.MANDATORY` 要求调用方已有事务；Outbox 让业务修改与事件保存一起提交或回滚。

**代码范围与上下文：** 两个完整方法，位于两个不同类。`markPayOrderSuccess` 所在类已继承 `ServiceImpl`；`append` 所在类已有 `PayMessageOutboxMapper mapper`、`ObjectMapper objectMapper`。当前项目使用 Jackson 3 的 `tools.jackson.databind.ObjectMapper` 和 `tools.jackson.core.JacksonException`，不要换成不匹配的导入。

**练习代码：**

```java
public boolean markPayOrderSuccess(Long id, LocalDateTime successTime) {
    // 1：准备写入支付成功状态与支付时间。
    return lambdaUpdate()
            .set(PayOrder::getStatus, __Q08_01__)
            .set(PayOrder::getPaySuccessTime, successTime)
            .eq(PayOrder::getId, id)

            // 2：只更新未提交或等待支付的记录，已成功记录不再更新。
            .in(PayOrder::getStatus, __Q08_02__, __Q08_03__)
            .update();
}
```

```java
// 3：此方法必须加入调用方事务，没有事务就拒绝执行。
@Transactional(propagation = __Q08_04__)
public void append(PaySuccessMessage event) {
    // 4：记录事件标识、事件类型和关联业务订单。
    PayMessageOutbox row = new PayMessageOutbox();
    row.setEventId(event.eventId());
    row.setEventType("PAY_SUCCESS");
    row.setAggregateId(event.orderId().toString());

    // 5：保存完整事件 JSON，序列化失败时抛出运行时异常。
    try {
        row.setPayload(__Q08_05__);
    } catch (JacksonException exception) {
        throw new IllegalArgumentException("Payment-success event cannot be serialized", exception);
    }

    // 6：初始化为待发送，写入事件发生时间和重试次数。
    row.setStatus(__Q08_06__);
    row.setRetryCount(0);
    row.setCreatedAt(event.paidAt());

    // 7：这里只插入数据库记录，后续投递由 relay 完成。
    __Q08_07__;
}
```

**自查场景：** 同一支付单第二次更新返回 false；在有事务的调用中，Outbox 插入失败应使该事务回滚。真实余额支付入口还有用户校验、锁支付单、校验业务订单、扣余额等步骤，并有 `@GlobalTransactional` 和 `@Transactional`；上面两个方法不能单独充当完整支付流程。

<a id="q09"></a>

## Q09：启动类上的 Mapper 和 Feign 配置

**该部分是完成什么工作的：** 启动购物车服务时注册 Mapper 和共享模块中的 Feign 客户端，使 `ItemClient` 能按商品服务名发起 HTTP 调用。

**原文件路径：** [CartServiceApplication.java](E:/ANotes/software/hmall-lottery/cart-service/src/main/java/com/dongqh/cartservice/CartServiceApplication.java)；[ItemClient.java](E:/ANotes/software/hmall-lottery/hm-api/src/main/java/com/dongqh/hmapi/client/ItemClient.java)。

**主题组件：** `@SpringBootApplication` 声明启动类；`@MapperScan` 注册 Mapper 接口；`@EnableFeignClients` 注册 Feign 接口；`@FeignClient` 声明服务名和降级工厂。

**代码范围与上下文：** 完整启动类、`ItemClient` 的单方法节选。`DefaultFeignConfig`、`ItemClientFallbackFactory` 已存在；本题不重写它们，也不复制接口其余库存方法。相关导入来自两个原文件。

**练习代码：**

```java
// 1：Feign 接口在共享 hm-api 包中，需显式扫描，并使用现有默认配置。
@EnableFeignClients(basePackages = "__Q09_01__",
        defaultConfiguration = DefaultFeignConfig.class)

// 2：扫描本服务的 Mapper 接口包。
@MapperScan("__Q09_02__")

// 3：声明 Spring Boot 启动类。
__Q09_03__
public class CartServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
```

```java
// 4：以商品服务的注册名为目标；沿用已有降级工厂。
@FeignClient(value = "__Q09_04__", fallbackFactory = ItemClientFallbackFactory.class)
public interface ItemClient {
    // 5：对应商品服务的批量查询接口，通过 ids 查询参数传入 ID 集合。
    @GetMapping("__Q09_05__")
    List<ItemDTO> queryItemByIds(@RequestParam("ids") Collection<Long> ids);
}
```

**自查场景：** 扫描包应是接口所在的包，不是调用方 Service 包；目标服务名应与商品服务的 `spring.application.name` 一致；远程接口路径应与商品 Controller 一致。声明降级工厂还需要现有项目的注册与 Sentinel 配置配合，不是写一个注解就能自行触发所有降级行为。

<a id="q10"></a>

## Q10：常用组件 YAML，以及配置属性类

**该部分是完成什么工作的：** 配置服务注册发现、外部 JDBC 配置、消息监听、Seata AT，以及登录令牌有效期和密码编码器。

**原文件路径：** [cart application.yaml](E:/ANotes/software/hmall-lottery/cart-service/src/main/resources/application.yaml)、[cart application-local.yaml](E:/ANotes/software/hmall-lottery/cart-service/src/main/resources/application-local.yaml)、[user application.yaml](E:/ANotes/software/hmall-lottery/user-service/src/main/resources/application.yaml)、[JwtProperties.java](E:/ANotes/software/hmall-lottery/user-service/src/main/java/com/dongqh/userservice/config/JwtProperties.java)、[SecurityConfig.java](E:/ANotes/software/hmall-lottery/user-service/src/main/java/com/dongqh/userservice/config/SecurityConfig.java)。

**主题组件：** Nacos 负责服务发现与配置；RabbitMQ 监听参数决定失败消息处理；Seata AT 通过数据源代理接入全局事务；属性绑定类把 `hm.jwt` 节点转换成 Java 对象。

**代码范围与上下文：** 各原文件的配置节选。每个代码块只放入标注的文件位置，保留原文件其余配置。类片段省去属性类中的密钥字段和配置类中的 `keyPair` Bean；练习副本要保留这些原有内容。

**练习 A：`cart-service/src/main/resources/application.yaml` 的相关节点。**

```yaml
spring:
  application:
    # 1：使用服务的注册名，供发现机制识别。
    name: __Q10_01__
  config:
    import:
      # 2：导入 Nacos 中的共享 JDBC 配置，沿用本项目的组与刷新参数。
      - "__Q10_02__"
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:18848}
      # 3：开启注册发现；账号等其余节点沿用原文件。
      discovery:
        enabled: true
        register-enabled: true
        group: DEFAULT_GROUP
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    virtual-host: ${RABBITMQ_VHOST:/dongqh}
    listener:
      simple:
        # 4：由监听容器依据执行结果处理确认，而不是业务代码手动 ack。
        acknowledge-mode: __Q10_03__
        # 5：拒绝后不默认重新入队，配合原有重试与死信配置。
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3

seata:
  enabled: ${SEATA_ENABLED:true}
  application-id: ${spring.application.name}
  # 6：使用项目统一的事务组。
  tx-service-group: __Q10_04__
  enable-auto-data-source-proxy: true
  # 7：指定关系型数据库的 AT 数据源代理模式。
  data-source-proxy-mode: __Q10_05__
```

**练习 B：`cart-service/src/main/resources/application-local.yaml` 的 `hm.db` 节点。**

```yaml
hm:
  db:
    # 8：本机通过 Docker 映射端口访问数据库；数据库属于购物车服务。
    host: 127.0.0.1
    port: ${HM_CART_DB_PORT:13307}
    database: __Q10_06__
    username: hm_cart_app
    password: ${HM_CART_DB_PASSWORD:change-cart-db-password}
```

`hm.db` 是这个项目的自定义节点，不等于 Spring Boot 的 `spring.datasource`。本题沿用项目已有共享 JDBC 配置的约定；启动服务时仍需准备该外部配置。根目录 README 说明默认使用 local profile，真实连接值由自己的环境配置提供。

**练习 C：用户服务 YAML 和 Java 属性绑定。**

```yaml
hm:
  jwt:
    # 9：令牌有效期为 30 分钟，使用带单位的 Duration 配置。
    tokenTTL: __Q10_07__
```

```java
@Data
// 10：把 hm.jwt 下的配置绑定到该类。
@ConfigurationProperties(prefix = "__Q10_08__")
public class JwtProperties {
    // 保留原类其余字段；此处只练习有效期，默认值仍为 10 分钟。
    private Duration tokenTTL = Duration.ofMinutes(10);
}

@Configuration
// 11：注册此属性类，让 Spring 可以注入它。
@EnableConfigurationProperties(__Q10_09__)
public class SecurityConfig {
    // 12：注册密码编码器，供登录验证和密码摘要生成使用。
    @Bean
    public PasswordEncoder passwordEncoder() {
        return __Q10_10__;
    }
    // 保留原配置类中的 keyPair Bean。
}
```

**自查场景：** 能解释服务名、事务组名、数据库名分别标识什么；Java 默认有效期为 10 分钟，而 YAML 明确配置时为 30 分钟；`auto` 是容器根据监听结果确认，不能解释为消息一投递就无条件确认。Seata 的 registry、vgroup mapping，RabbitMQ 账号与队列绑定等未列出的配置仍要保留。

<a id="q11"></a>

## Q11：Redis Lua 原子预占次数，并避免重试重复扣次数

**该部分是完成什么工作的：** 同一活动中，同一用户每天的抽奖次数有限。一次请求要同时完成查重、判断剩余次数、预占次数、记录请求状态和超时索引。重复请求应返回原结果。

**原文件路径：** [reserve_draw_quota.lua](E:/ANotes/software/hmall-lottery/lottery-service/src/main/resources/redis/lottery/reserve_draw_quota.lua)；调用方 [RedisDrawQuotaService.java](E:/ANotes/software/hmall-lottery/lottery-service/src/main/java/com/dongqh/lotteryservice/quota/RedisDrawQuotaService.java) 的 `reserve`。

**主题组件：** Redis 字符串存已用次数，Hash 存预占记录，ZSet 存超时索引；Lua 将检查和修改放在一次服务器端脚本执行中。此题是 `lottery-service` 补充题。

**代码范围与上下文：** 完整预占 Lua 脚本，沿用当前项目的单节点 Redis 使用方式。Java 调用通过 `StringRedisTemplate.execute(RESERVE_SCRIPT, keys, args...)`，脚本从 classpath 加载，返回类型是 `List`。

| 参数 | 含义 |
| --- | --- |
| `KEYS[1]` | 次数键，例如 `draw:quota:1:1001:20261006` |
| `KEYS[2]` | 请求记录键，例如 `draw:reservation:req-001` |
| `KEYS[3]` | 超时有序集合，`draw:reservation:timeouts` |
| `ARGV[1..6]` | 请求 ID、活动 ID、用户 ID、本次次数、当日上限、抽奖日期 |
| `ARGV[7..9]` | 创建时间、处理超时时间、记录过期时间，均为毫秒时间戳 |

**练习代码：**

```lua
local quotaKey = KEYS[1]
local reservationKey = KEYS[2]
local timeoutKey = KEYS[3]

local requestId = ARGV[1]
local activityId = ARGV[2]
local userId = ARGV[3]
local drawCount = tonumber(ARGV[4])
local dailyLimit = tonumber(ARGV[5])
local drawDate = ARGV[6]
local createdAt = ARGV[7]
local timeoutAt = ARGV[8]
local expiresAt = ARGV[9]

-- 1：先查请求是否存在。重试应先匹配原请求，不能再次预占次数。
if redis.call('__Q11_01__', reservationKey) == 1 then
    local existing = redis.call('HMGET', reservationKey,
            'activityId', 'userId', 'drawCount', 'drawDate', 'status')

    -- 2：同一请求 ID 携带不同活动、用户或次数时，返回冲突代码 3。
    if existing[1] ~= activityId or existing[2] ~= userId
            or existing[3] ~= tostring(drawCount) then
        return {3, existing[5] or 'RESERVED', existing[4] or drawDate,
                existing[3] or tostring(drawCount)}
    end

    -- 3：合法重试返回已有状态和原日期，代码 0 表示重复请求。
    return {0, existing[5] or 'RESERVED', existing[4], existing[3]}
end

-- 4：读取已用次数；没有此键时按 0 处理。
local used = tonumber(redis.call('__Q11_02__', quotaKey) or '0')

-- 5：本次预占后超过当日上限时，返回代码 2，不修改次数。
if __Q11_03__ then
    return {2, 'RESERVED', drawDate, tostring(drawCount)}
end

-- 6：增加已用次数，并设置绝对毫秒过期时间。
redis.call('__Q11_04__', quotaKey, drawCount)
redis.call('__Q11_05__', quotaKey, expiresAt)

-- 7：保存请求的身份与 RESERVED 状态，供确认、释放和重试使用。
redis.call('HSET', reservationKey,
        'requestId', requestId,
        'activityId', activityId,
        'userId', userId,
        'drawCount', tostring(drawCount),
        'drawDate', drawDate,
        'status', 'RESERVED',
        'createdAt', createdAt)
redis.call('PEXPIREAT', reservationKey, expiresAt)

-- 8：按处理截止时间建立超时索引，代码 1 表示创建了新预占。
redis.call('__Q11_06__', timeoutKey, timeoutAt, requestId)
return {1, 'RESERVED', drawDate, tostring(drawCount)}
```

**自查场景：** 已用 9、上限 10、本次 1 可创建；已用 10 再抽 1 被拒绝；同请求重试不增加次数；同请求 ID 改变用户或抽奖次数返回冲突。重复请求检查故意不比较新 `dailyLimit`：服务策略变化不应让已存在请求的重试重新扣次数。

本题练的是原子配额和幂等，不是缓存穿透、缓存击穿或缓存雪崩。当前多键命名没有统一 Redis Cluster hash tag，不应直接当作 Redis Cluster 多键脚本方案。Lua 的原子执行保证也不等于出错时自动回滚已执行的写命令。[Redis Lua 官方说明](https://redis.io/docs/latest/develop/programmability/eval-intro/)

<a id="q12"></a>

## Q12：Redisson 连接配置、加锁和释放锁

**该部分是完成什么工作的：** 按“活动 ID + 用户 ID”创建锁，使使用相同锁名的服务实例协调同一用户的抽奖动作。业务结束或抛出异常时都要释放自己持有的锁。

**原文件路径：** [RedissonDrawLockService.java](E:/ANotes/software/hmall-lottery/lottery-service/src/main/java/com/dongqh/lotteryservice/lock/RedissonDrawLockService.java)，定位 `execute`；[RedissonConfig.java](E:/ANotes/software/hmall-lottery/lottery-service/src/main/java/com/dongqh/lotteryservice/config/RedissonConfig.java)；[lottery application.yml](E:/ANotes/software/hmall-lottery/lottery-service/src/main/resources/application.yml)。键名定义见 [DrawQuotaKeys.java](E:/ANotes/software/hmall-lottery/lottery-service/src/main/java/com/dongqh/lotteryservice/quota/DrawQuotaKeys.java)。

**主题组件：** Spring 的 Redis 属性绑定负责读取连接配置；Redisson 创建客户端和分布式锁；`finally` 保证释放步骤在正常返回和异常时均被执行。

**代码范围与上下文：** YAML 节选、完整 Redisson 配置类、完整执行方法。执行方法所在类已有 `RedissonClient redissonClient` 和 `LotteryProperties properties`，实现 `DrawLockService`。相关导入为 `RLock`、`TimeUnit`、`Supplier` 等原类导入；配置类的 Redis 属性类型使用本项目 Boot 4 的 `org.springframework.boot.data.redis.autoconfigure.DataRedisProperties`。

**练习 A：Redis 连接节点。**

```yaml
spring:
  data:
    # 1：使用 Spring 的 Redis 节点，保留环境变量作为连接值来源。
    __Q12_01__:
      host: ${LOTTERY_REDIS_HOST}
      port: ${LOTTERY_REDIS_PORT:6379}
      database: ${LOTTERY_REDIS_DATABASE:0}
      password: ${LOTTERY_REDIS_PASSWORD:}
```

**练习 B：把连接属性交给 Redisson。**

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataRedisProperties.class)
public class RedissonConfig {
    // 2：应用关闭时调用 shutdown，释放客户端资源。
    @Bean(destroyMethod = "__Q12_02__")
    public RedissonClient redissonClient(DataRedisProperties redisProperties) {
        Config config = new Config();
        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.setPassword(redisProperties.getPassword());
        }

        // 3：本项目使用单节点连接，指定 redis:// 地址和数据库编号。
        config.useSingleServer()
                .setAddress(__Q12_03__)
                .setDatabase(redisProperties.getDatabase());
        return __Q12_04__;
    }
}
```

**练习 C：执行受锁保护的业务。**

```java
public <T> T execute(long activityId, long userId, Supplier<T> action) {
    // 4：键名格式为 lock:draw:活动ID:用户ID，避免所有用户共用一把锁。
    RLock lock = __Q12_05__;
    boolean acquired = false;

    try {
        // 5：最多等待配置中的 lockWait；此重载没有指定固定 leaseTime。
        acquired = __Q12_06__;
        if (!acquired || !lock.isHeldByCurrentThread()) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_LOCK_UNAVAILABLE);
        }

        // 6：成功取得锁后，同步执行传入业务并返回其结果。
        return __Q12_07__;
    } catch (InterruptedException error) {
        // 7：恢复线程中断标记，再转换为项目领域异常。
        __Q12_08__;
        throw new LotteryDomainException(LotteryErrorCode.DRAW_LOCK_UNAVAILABLE);
    } finally {
        // 8：仅释放已取得、且仍由当前线程持有的锁。
        if (__Q12_09__) {
            __Q12_10__;
        }
    }
}
```

**自查场景：** 相同活动、相同用户争用同一把锁；不同用户使用不同锁；取锁失败时不执行 `action`；业务抛异常时也走释放逻辑；等待被中断时恢复中断标记。

本题的 `lockWait` 是取锁最多等待多久，不是锁的存活时间。此 `tryLock(waitTime, unit)` 调用未指定固定租期，由 Redisson 的 watchdog 机制维护锁的过期时间。锁也只协调遵循同一锁协议的调用，仍需 Q11 的原子配额和 Q07、Q08 类似的持久化状态约束。[Redisson 锁官方说明](https://redisson.pro/docs/data-and-services/locks-and-synchronizers/)

---

# 参考答案

先完成题目再读这一部分。答案保留题目中的中文步骤注释；如果你的写法不同，可以按自查场景比较行为。

<a id="a01"></a>

## Q01 答案：查询购物车并补充商品信息

```java
public List<CartVO> queryMyCarts() {
    // 1：只查询当前登录用户的记录，不能查出其他人的购物车。
    List<Cart> carts = query().eq("user_id", UserContext.getUser()).list();

    // 2：购物车为空时直接返回空列表，减少不必要的远程调用。
    if (CollUtils.isEmpty(carts)) {
        return CollUtils.emptyList();
    }

    // 3：把数据库实体转换为页面使用的 VO 列表。
    List<CartVO> vos = BeanUtils.copyList(carts, CartVO.class);

    // 4：调用下面的辅助方法，填充商品最新信息。
    handleCartItems(vos);
    return vos;
}

private void handleCartItems(List<CartVO> vos) {
    // 5：从购物车提取商品 ID，使用 Set 去重。
    Set<Long> itemIds = vos.stream()
            .map(CartVO::getItemId)
            .collect(Collectors.toSet());

    // 6：批量查询商品，一次请求传入所有 ID。
    List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
    if (CollUtil.isEmpty(items)) {
        return;
    }

    // 7：建立“商品 ID -> 商品信息”的映射，便于逐条补充购物车。
    Map<Long, ItemDTO> itemMap = items.stream()
            .collect(Collectors.toMap(ItemDTO::getId, item -> item));

    for (CartVO cart : vos) {
        // 8：按当前条目的商品 ID 查找；商品未返回时跳过。
        ItemDTO item = itemMap.get(cart.getItemId());
        if (item == null) {
            continue;
        }

        // 9：填充最新价格、状态、库存，保留原来的购物车条目数据。
        cart.setNewPrice(item.getPrice());
        cart.setStatus(item.getStatus());
        cart.setStock(item.getStock());
    }
}
```

**为什么这样写：** 用户过滤决定数据归属；批量调用避免为每条购物车记录单独发一次请求；Map 避免为每条记录重复遍历商品列表。`price` 保存购物车原价格，`newPrice` 表示本次查询得到的最新价格。这里的空商品结果沿用源码的降级行为，不代表商品有效性已得到确认。

<a id="a02"></a>

## Q02 答案：添加与批量删除

```java
public void addItem2Cart(CartFormDTO form) {
    // 1：从当前请求上下文取用户，不信任前端传来的用户 ID。
    Long userId = UserContext.getUser();

    // 2：商品已经在当前用户的购物车里时，更新数量并结束。
    if (checkItemExists(form.getItemId(), userId)) {
        baseMapper.updateNum(form.getItemId(), userId);
        return;
    }

    // 3：新条目才需要检查购物车条目数上限。
    checkCartsFull(userId);

    // 4：将请求转换为数据库实体，明确绑定当前用户。
    Cart cart = BeanUtils.copyBean(form, Cart.class);
    cart.setUserId(userId);

    // 5：保存新条目。
    save(cart);
}

public void removeByItemIds(Collection<Long> itemIds) {
    // 6：这里的 itemIds 是商品 ID，不是购物车记录的主键 ID。
    QueryWrapper<Cart> wrapper = new QueryWrapper<Cart>()
            .eq("user_id", UserContext.getUser())
            .in("item_id", itemIds);

    // 7：根据两个条件删除匹配的购物车条目。
    remove(wrapper);
}
```

**为什么这样写：** 已有商品走数量更新，新商品才检查条目数。Mapper 实际执行 `UPDATE cart SET num = num + 1 WHERE user_id = #{userId} AND item_id = #{itemId}`，不是在 Java 里读出数量再加 1。批量删除同时包含用户和商品两个条件，不能拿商品 ID 当条目主键直接调用 `removeByIds`。

<a id="a03"></a>

## Q03 答案：商品 CRUD

```java
@PostMapping
public void saveItem(@RequestBody ItemDTO item) {
    // 1：DTO 转为商品实体后新增。
    itemService.save(BeanUtils.copyBean(item, Item.class));
}

@GetMapping("{id}")
public ItemDTO queryItemById(@PathVariable("id") Long id) {
    // 2：按主键查询实体，转换成 DTO 返回。
    return BeanUtils.copyBean(itemService.getById(id), ItemDTO.class);
}

@PutMapping
public void updateItem(@RequestBody ItemDTO item) {
    // 3：普通资料修改不能顺便修改上下架状态。
    item.setStatus(null);

    // 4：按 DTO 中的 ID 更新实体。
    itemService.updateById(BeanUtils.copyBean(item, Item.class));
}

@DeleteMapping("{id}")
public void deleteItemById(@PathVariable("id") Long id) {
    // 5：按主键删除商品。
    itemService.removeById(id);
}
```

**为什么这样写：** `save` 是插入，`updateById` 依赖对象里的 ID 定位已有记录。把 `status` 设为 null 是利用当前更新策略忽略该字段，并把上下架动作留给状态接口；它不是把数据库状态列清空。

<a id="a04"></a>

## Q04 答案：分页与插件

```java
@GetMapping("/page")
public PageDTO<ItemDTO> queryItemByPage(PageQuery query) {
    // 1：构造分页参数，默认按 update_time 倒序，并查询数据库。
    Page<Item> result = itemService.page(query.toMpPage("update_time", false));

    // 2：保留总数和总页数，将记录转为 ItemDTO。
    return PageDTO.of(result, ItemDTO.class);
}
```

```java
@Configuration
@ConditionalOnClass({MybatisPlusInterceptor.class, BaseMapper.class})
public class MyBatisConfig {
    // 3：把拦截器注册为 Bean；已有同类型 Bean 时避免重复创建。
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 4：使用 MySQL 类型的分页拦截器。
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);

        // 5：将单页上限设为 1000 条，然后装入总拦截器。
        pagination.setMaxLimit(1000L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
```

**为什么这样写：** `PageQuery` 包含页码和每页数量，`false` 表示默认倒序；`PageDTO.of` 输出的是总数、总页数和转换后的记录，不只是一个 List。分页对象和分页插件一起完成这里的分页查询。

<a id="a05"></a>

## Q05 答案：用户登录

```java
public UserLoginVO login(LoginFormDTO form) {
    // 1：读取表单中的用户名和明文密码。
    String username = form.getUsername();
    String password = form.getPassword();

    // 2：按用户名查询唯一用户；没有此用户则结束登录。
    User user = lambdaQuery().eq(User::getUsername, username).one();
    Assert.notNull(user, "用户名错误");

    // 3：被冻结的用户不能登录。
    if (user.getStatus() == UserStatus.FROZEN) {
        throw new ForbiddenException("用户被冻结");
    }

    // 4：使用编码器验证密码，不能直接比较输入与数据库摘要。
    if (!passwordEncoder.matches(password, user.getPassword())) {
        throw new BadRequestException("用户名或密码错误");
    }

    // 5：令牌写入用户身份和角色，并使用配置中的有效期。
    String token = jwtTool.createToken(user.getId(), user.getRole(), jwtProperties.getTokenTTL());

    // 6：返回页面需要的登录信息，不返回密码摘要。
    UserLoginVO vo = new UserLoginVO();
    vo.setUserId(user.getId());
    vo.setUsername(user.getUsername());
    vo.setBalance(user.getBalance());
    vo.setToken(token);
    return vo;
}
```

**为什么这样写：** `matches` 的第一个参数是用户输入的明文，第二个参数是数据库摘要。BCrypt 生成摘要时使用盐值，不能重新 `encode` 一次然后用字符串相等判断密码。令牌有效期来自配置；角色也沿用当前项目的令牌生成接口。

<a id="a06"></a>

## Q06 答案：条件扣库存

```java
// 1：扣减数量为正，且当前库存足够时，才更新指定商品。
@Update("UPDATE item SET stock = stock - #{num} " +
        "WHERE id = #{itemId} AND #{num} > 0 AND stock >= #{num}")
int updateStock(OrderDetailDTO orderDetail);
```

```java
public void deductStock(List<OrderDetailDTO> items) {
    // 2：空明细不能执行库存扣减。
    if (items == null || items.isEmpty()) {
        throw new BizIllegalException("扣减库存明细不能为空！");
    }

    for (OrderDetailDTO item : items) {
        // 3：每条明细都必须有商品 ID 和正整数数量。
        if (item == null || item.getItemId() == null
                || item.getNum() == null || item.getNum() <= 0) {
            throw new BizIllegalException("扣减库存参数不合法！");
        }

        // 4：检查 SQL 实际更新行数；没有更新成功就抛出异常。
        if (baseMapper.updateStock(item) != 1) {
            throw new BizIllegalException("库存不足！");
        }
    }
}
```

**为什么这样写：** “检查库存”和“修改库存”必须在同一条 SQL 中，不能让两个请求分别根据旧库存判断成功。受影响行数为 0 可能是商品不存在或库存不足，当前源码统一抛出库存不足异常。

**关联练习方向：** [UserMapper.java](E:/ANotes/software/hmall-lottery/user-service/src/main/java/com/dongqh/userservice/mapper/UserMapper.java) 的 `updateMoney` 使用相同思路：`balance = balance - #{totalFee}`，同时要求 `#{totalFee} > 0 AND balance >= #{totalFee}`。可以在理解本题后对照它阅读余额扣减。

<a id="a07"></a>

## Q07 答案：行锁与重复回调

```java
// 1：按订单 ID 查询，并在当前事务内对记录加写锁。
@org.apache.ibatis.annotations.Select("SELECT * FROM `order` WHERE id = #{id} FOR UPDATE")
Order selectByIdForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);
```

```java
// 2：让行锁覆盖本方法中的查询、校验和更新过程。
@Transactional
public void markOrderPaySuccess(Long orderId, Long userId,
                                Integer amount, LocalDateTime paidAt) {
    // 3：拒绝缺失参数和非法金额。
    if (orderId == null || userId == null || amount == null
            || amount <= 0 || paidAt == null) {
        throw new BadRequestException("订单支付参数不合法");
    }

    // 4：查询并锁定订单；校验消息对应的用户和金额。
    Order order = baseMapper.selectByIdForUpdate(orderId);
    if (order == null || !userId.equals(order.getUserId())
            || !amount.equals(order.getTotalFee())) {
        throw new BadRequestException("订单状态或金额已发生变化");
    }

    // 5：合法的重复回调不再次修改支付时间。
    if (Integer.valueOf(2).equals(order.getStatus())) {
        return;
    }

    // 6：其余状态中，只有待支付订单可以进入已支付状态。
    if (!Integer.valueOf(1).equals(order.getStatus())) {
        throw new BadRequestException("订单状态或金额已发生变化");
    }

    // 7：写入已支付状态和消息携带的支付时间。
    order.setStatus(2);
    order.setPayTime(paidAt);

    // 8：必须确实更新一行，否则抛出异常。
    if (baseMapper.updateById(order) != 1) {
        throw new BadRequestException("订单状态或金额已发生变化");
    }
}
```

**为什么这样写：** 锁让竞争同一订单记录的事务协调执行；状态判断让合法重试直接返回。先校验用户和金额，再判断已支付，避免错误消息被当作合法重复消息。数据库提交或回滚时释放这里的行锁，不需要 Java 手动 `unlock`。

<a id="a08"></a>

## Q08 答案：支付状态与 Outbox

```java
public boolean markPayOrderSuccess(Long id, LocalDateTime successTime) {
    // 1：准备写入支付成功状态与支付时间。
    return lambdaUpdate()
            .set(PayOrder::getStatus, PayStatus.TRADE_SUCCESS.getValue())
            .set(PayOrder::getPaySuccessTime, successTime)
            .eq(PayOrder::getId, id)

            // 2：只更新未提交或等待支付的记录，已成功记录不再更新。
            .in(PayOrder::getStatus, PayStatus.NOT_COMMIT.getValue(), PayStatus.WAIT_BUYER_PAY.getValue())
            .update();
}
```

```java
// 3：此方法必须加入调用方事务，没有事务就拒绝执行。
@Transactional(propagation = Propagation.MANDATORY)
public void append(PaySuccessMessage event) {
    // 4：记录事件标识、事件类型和关联业务订单。
    PayMessageOutbox row = new PayMessageOutbox();
    row.setEventId(event.eventId());
    row.setEventType("PAY_SUCCESS");
    row.setAggregateId(event.orderId().toString());

    // 5：保存完整事件 JSON，序列化失败时抛出运行时异常。
    try {
        row.setPayload(objectMapper.writeValueAsString(event));
    } catch (JacksonException exception) {
        throw new IllegalArgumentException("Payment-success event cannot be serialized", exception);
    }

    // 6：初始化为待发送，写入事件发生时间和重试次数。
    row.setStatus(PayOutboxStatus.PENDING);
    row.setRetryCount(0);
    row.setCreatedAt(event.paidAt());

    // 7：这里只插入数据库记录，后续投递由 relay 完成。
    mapper.insert(row);
}
```

**为什么这样写：** 状态写在 UPDATE 条件里，避免并发请求都根据之前的查询结果重复更新成功。`MANDATORY` 避免独立提交事件而业务尚未提交的情况。原调用方检查更新返回值，再扣款、写 Outbox；任一步失败会让参与该事务的数据库修改回滚。

Outbox 后续由 [PayOutboxRelay.java](E:/ANotes/software/hmall-lottery/pay-service/src/main/java/com/dongqh/payservice/messaging/PayOutboxRelay.java) 和 [PaySuccessPublisher.java](E:/ANotes/software/hmall-lottery/pay-service/src/main/java/com/dongqh/payservice/messaging/PaySuccessPublisher.java) 处理。消费者仍需处理重复消息；Seata AT 不会把 RabbitMQ 已投递消息或 Redis 写入自动回滚。

<a id="a09"></a>

## Q09 答案：启动类和 Feign 接口

```java
// 1：Feign 接口在共享 hm-api 包中，需显式扫描，并使用现有默认配置。
@EnableFeignClients(basePackages = "com.dongqh.hmapi.client",
        defaultConfiguration = DefaultFeignConfig.class)

// 2：扫描本服务的 Mapper 接口包。
@MapperScan("com.dongqh.cartservice.mapper")

// 3：声明 Spring Boot 启动类。
@SpringBootApplication
public class CartServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
```

```java
// 4：以商品服务的注册名为目标；沿用已有降级工厂。
@FeignClient(value = "item-service", fallbackFactory = ItemClientFallbackFactory.class)
public interface ItemClient {
    // 5：对应商品服务的批量查询接口，通过 ids 查询参数传入 ID 集合。
    @GetMapping("/items")
    List<ItemDTO> queryItemByIds(@RequestParam("ids") Collection<Long> ids);
}
```

**为什么这样写：** 共享接口不在购物车启动类的默认包扫描范围内，需要明确注册。Feign 的服务名用于服务发现，`/items` 是 HTTP 接口路径；二者不能互换。

<a id="a10"></a>

## Q10 答案：YAML 与属性绑定

**A：购物车主配置的相关节点。**

```yaml
spring:
  application:
    # 1：使用服务的注册名，供发现机制识别。
    name: cart-service
  config:
    import:
      # 2：导入 Nacos 中的共享 JDBC 配置，沿用本项目的组与刷新参数。
      - "optional:nacos:shared.jdbc.yaml?group=DEFAULT_GROUP&refreshEnabled=true"
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:18848}
      # 3：开启注册发现；账号等其余节点沿用原文件。
      discovery:
        enabled: true
        register-enabled: true
        group: DEFAULT_GROUP
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    virtual-host: ${RABBITMQ_VHOST:/dongqh}
    listener:
      simple:
        # 4：由监听容器依据执行结果处理确认，而不是业务代码手动 ack。
        acknowledge-mode: auto
        # 5：拒绝后不默认重新入队，配合原有重试与死信配置。
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3

seata:
  enabled: ${SEATA_ENABLED:true}
  application-id: ${spring.application.name}
  # 6：使用项目统一的事务组。
  tx-service-group: hmall_tx_group
  enable-auto-data-source-proxy: true
  # 7：指定关系型数据库的 AT 数据源代理模式。
  data-source-proxy-mode: AT
```

**B：购物车本地数据库节点。**

```yaml
hm:
  db:
    # 8：本机通过 Docker 映射端口访问数据库；数据库属于购物车服务。
    host: 127.0.0.1
    port: ${HM_CART_DB_PORT:13307}
    database: hm_cart
    username: hm_cart_app
    password: ${HM_CART_DB_PASSWORD:change-cart-db-password}
```

**C：用户配置与属性绑定。**

```yaml
hm:
  jwt:
    # 9：令牌有效期为 30 分钟，使用带单位的 Duration 配置。
    tokenTTL: 30m
```

```java
@Data
// 10：把 hm.jwt 下的配置绑定到该类。
@ConfigurationProperties(prefix = "hm.jwt")
public class JwtProperties {
    // 保留原类其余字段；此处只练习有效期，默认值仍为 10 分钟。
    private Duration tokenTTL = Duration.ofMinutes(10);
}

@Configuration
// 11：注册此属性类，让 Spring 可以注入它。
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    // 12：注册密码编码器，供登录验证和密码摘要生成使用。
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    // 保留原配置类中的 keyPair Bean。
}
```

**为什么这样写：** YAML 决定配置值，`@ConfigurationProperties` 决定绑定前缀，`@EnableConfigurationProperties` 注册属性对象，`@Bean` 注册实际可注入的组件。仅写一个带前缀注解的属性类，不等于已把它注册到容器。

这里的 `optional:nacos` 允许配置资源缺失时不因“该资源不存在”本身中断导入，但数据库仍需要完整有效的连接配置。`retry.max-attempts: 3` 是最多 3 次尝试，不是“首次失败后再重试 3 次”。死信去向由现有队列的死信交换机绑定决定，可继续查看 [OrderCreatedListener.java](E:/ANotes/software/hmall-lottery/cart-service/src/main/java/com/dongqh/cartservice/listener/OrderCreatedListener.java) 和 [CartMessagingConfiguration.java](E:/ANotes/software/hmall-lottery/cart-service/src/main/java/com/dongqh/cartservice/config/CartMessagingConfiguration.java)。

<a id="a11"></a>

## Q11 答案：Redis Lua 预占

```lua
local quotaKey = KEYS[1]
local reservationKey = KEYS[2]
local timeoutKey = KEYS[3]

local requestId = ARGV[1]
local activityId = ARGV[2]
local userId = ARGV[3]
local drawCount = tonumber(ARGV[4])
local dailyLimit = tonumber(ARGV[5])
local drawDate = ARGV[6]
local createdAt = ARGV[7]
local timeoutAt = ARGV[8]
local expiresAt = ARGV[9]

-- 1：先查请求是否存在。重试应先匹配原请求，不能再次预占次数。
if redis.call('EXISTS', reservationKey) == 1 then
    local existing = redis.call('HMGET', reservationKey,
            'activityId', 'userId', 'drawCount', 'drawDate', 'status')

    -- 2：同一请求 ID 携带不同活动、用户或次数时，返回冲突代码 3。
    if existing[1] ~= activityId or existing[2] ~= userId
            or existing[3] ~= tostring(drawCount) then
        return {3, existing[5] or 'RESERVED', existing[4] or drawDate,
                existing[3] or tostring(drawCount)}
    end

    -- 3：合法重试返回已有状态和原日期，代码 0 表示重复请求。
    return {0, existing[5] or 'RESERVED', existing[4], existing[3]}
end

-- 4：读取已用次数；没有此键时按 0 处理。
local used = tonumber(redis.call('GET', quotaKey) or '0')

-- 5：本次预占后超过当日上限时，返回代码 2，不修改次数。
if used + drawCount > dailyLimit then
    return {2, 'RESERVED', drawDate, tostring(drawCount)}
end

-- 6：增加已用次数，并设置绝对毫秒过期时间。
redis.call('INCRBY', quotaKey, drawCount)
redis.call('PEXPIREAT', quotaKey, expiresAt)

-- 7：保存请求的身份与 RESERVED 状态，供确认、释放和重试使用。
redis.call('HSET', reservationKey,
        'requestId', requestId,
        'activityId', activityId,
        'userId', userId,
        'drawCount', tostring(drawCount),
        'drawDate', drawDate,
        'status', 'RESERVED',
        'createdAt', createdAt)
redis.call('PEXPIREAT', reservationKey, expiresAt)

-- 8：按处理截止时间建立超时索引，代码 1 表示创建了新预占。
redis.call('ZADD', timeoutKey, timeoutAt, requestId)
return {1, 'RESERVED', drawDate, tostring(drawCount)}
```

**为什么这样写：** 把 GET、次数判断和 INCRBY 分成不同 Java 请求时，其他请求可能在中间修改次数；在同一次脚本执行中不会发生这样的命令穿插。`PEXPIREAT` 传入的是绝对毫秒时间戳；不能换成接受相对秒数的 `EXPIRE` 而沿用同一个参数。

返回码含义为 0 重复、1 新建、2 配额不足、3 幂等冲突。Java 调用方还会检查参数并把返回值转换为领域结果。成功后执行确认、失败时释放、超时后对账属于其他现有流程，不是本脚本单独完成的工作。原子执行行为依据 [Redis Lua 官方说明](https://redis.io/docs/latest/develop/programmability/eval-intro/)。

<a id="a12"></a>

## Q12 答案：Redisson 配置与锁

**A：Redis 连接节点。**

```yaml
spring:
  data:
    # 1：使用 Spring 的 Redis 节点，保留环境变量作为连接值来源。
    redis:
      host: ${LOTTERY_REDIS_HOST}
      port: ${LOTTERY_REDIS_PORT:6379}
      database: ${LOTTERY_REDIS_DATABASE:0}
      password: ${LOTTERY_REDIS_PASSWORD:}
```

**B：客户端配置。**

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataRedisProperties.class)
public class RedissonConfig {
    // 2：应用关闭时调用 shutdown，释放客户端资源。
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(DataRedisProperties redisProperties) {
        Config config = new Config();
        if (StringUtils.hasText(redisProperties.getPassword())) {
            config.setPassword(redisProperties.getPassword());
        }

        // 3：本项目使用单节点连接，指定 redis:// 地址和数据库编号。
        config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase());
        return Redisson.create(config);
    }
}
```

**C：受锁保护的业务执行方法。**

```java
public <T> T execute(long activityId, long userId, Supplier<T> action) {
    // 4：键名格式为 lock:draw:活动ID:用户ID，避免所有用户共用一把锁。
    RLock lock = redissonClient.getLock(DrawQuotaKeys.drawLock(activityId, userId));
    boolean acquired = false;

    try {
        // 5：最多等待配置中的 lockWait；此重载没有指定固定 leaseTime。
        acquired = lock.tryLock(properties.lockWait().toMillis(), TimeUnit.MILLISECONDS);
        if (!acquired || !lock.isHeldByCurrentThread()) {
            throw new LotteryDomainException(LotteryErrorCode.DRAW_LOCK_UNAVAILABLE);
        }

        // 6：成功取得锁后，同步执行传入业务并返回其结果。
        return action.get();
    } catch (InterruptedException error) {
        // 7：恢复线程中断标记，再转换为项目领域异常。
        Thread.currentThread().interrupt();
        throw new LotteryDomainException(LotteryErrorCode.DRAW_LOCK_UNAVAILABLE);
    } finally {
        // 8：仅释放已取得、且仍由当前线程持有的锁。
        if (acquired && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**为什么这样写：** 锁的粒度由键名决定；当前方案同一用户在同一活动中争用一把锁。`action.get()` 在持锁线程内同步执行，`finally` 处理正常返回和异常。当前线程持有检查是为了遵守 Redisson 的锁所有权规则。[Redisson 锁官方说明](https://redisson.pro/docs/data-and-services/locks-and-synchronizers/)

仓库的 `luckyhub.lottery.lock-wait` 默认是 `3s`，由 `LotteryProperties.lockWait()` 提供。它控制等待获取锁的时长；本方法并未调用三参数的 `tryLock(waitTime, leaseTime, unit)`。

---

## 做完之后的复盘

每题写一句回答：这段代码负责什么；数据范围由哪个条件限制；失败时在哪里退出；若存在并发，哪一条 SQL、脚本或锁负责协调。

只要能解释这些点，并能独立补出代码，就比记住方法名更接近理解这个项目。
