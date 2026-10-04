package com.dongqh.fulfillmentservice.shipping.service;

import com.dongqh.fulfillmentservice.enums.FulfillmentStatus;
import com.dongqh.fulfillmentservice.model.FulfillmentPage;
import com.dongqh.fulfillmentservice.model.FulfillmentTaskView;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskOperationService;
import com.dongqh.fulfillmentservice.service.FulfillmentTaskReadService;
import com.dongqh.fulfillmentservice.shipping.domain.ShippingDomainException;
import com.dongqh.fulfillmentservice.shipping.domain.ShippingErrorCode;
import com.dongqh.fulfillmentservice.shipping.enums.ShippingStatus;
import com.dongqh.fulfillmentservice.shipping.model.ShippingOrderAdminQuery;
import com.dongqh.fulfillmentservice.shipping.model.ShippingOrderView;
import com.dongqh.fulfillmentservice.shipping.model.ShippingTrackingView;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingOrder;
import com.dongqh.fulfillmentservice.shipping.persistence.ShippingOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ShippingAdminService {
    private static final Pattern UNSAFE_NOTE = Pattern.compile("(?i)(?:"
            + "[\\p{Cc}\\p{Cf}\\{\\}\\[\\]]"
            + "|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"
            + "|(?<!\\d)1[3-9]\\d{9}(?!\\d)"
            + "|(?<!\\d)(?:\\d{17}[0-9Xx]|\\d{15})(?!\\d)"
            + "|(?<!\\d)0\\d{2,3}[- ]?\\d{7,8}(?!\\d)"
            + "|\\b(?:exception|payload|ciphertext|secret|authorization|provider\\s+response)\\b"
            + "|异常堆栈|原始响应|供应商响应|密文|明文|密钥|秘密|授权头|签名"
            + "|收件人|姓名|联系人|手机号|手机号码|电话号码|电话|座机|身份证|证件号|住址|详细地址"
            + "|(?:省|自治区|特别行政区).{0,30}(?:市|州).{0,30}(?:区|县).{0,50}(?:路|街|道|巷|弄|号|栋|单元|室)"
            + ")");
    private static final Pattern SAFE_NOTE = Pattern.compile("^(?:(?:"
            + "人工核验通过|已核对安全数据|已核对商品资料|确认可重试|可以重新发货"
            + "|用户申请终止|停止处理|重复任务|无需继续履约|隔离任务已核验|系统已恢复"
            + "|运维确认|安全原因|工单\\s*[：:#-]?\\s*[A-Za-z0-9_-]{1,32}"
            + ")[\\s，。；、,;]*)+$");

    private final ShippingOrderRepository orders;
    private final ShippingOrderService shipping;
    private final ShippingTrackingQueryService tracking;
    private final FulfillmentTaskReadService tasks;
    private final FulfillmentTaskOperationService taskOperations;
    private final ShippingOrderFulfillmentProjector projector;

    public ShippingAdminService(ShippingOrderRepository orders, ShippingOrderService shipping,
                                ShippingTrackingQueryService tracking, FulfillmentTaskReadService tasks,
                                FulfillmentTaskOperationService taskOperations,
                                ShippingOrderFulfillmentProjector projector) {
        this.orders = orders;
        this.shipping = shipping;
        this.tracking = tracking;
        this.tasks = tasks;
        this.taskOperations = taskOperations;
        this.projector = projector;
    }

    @Transactional(readOnly = true)
    public FulfillmentPage<ShippingOrderView> page(ShippingOrderAdminQuery query) {
        if (query == null) throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        long offset;
        try { offset = Math.multiplyExact(query.getPage() - 1, query.getSize()); }
        catch (ArithmeticException exception) { throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID); }
        List<Long> ids = orders.findAdminPage(query.getStatus(), query.getSourceType(), query.getSourceId(),
                query.getTargetUserId(), query.getWaybillNo(), query.getSize(), offset);
        long total = orders.countAdmin(query.getStatus(), query.getSourceType(), query.getSourceId(),
                query.getTargetUserId(), query.getWaybillNo());
        Map<Long, ShippingOrderView> views = shipping.getByIds(ids).stream()
                .collect(Collectors.toMap(ShippingOrderView::id, value -> value));
        List<ShippingOrderView> ordered = ids.stream().map(views::get).filter(Objects::nonNull).toList();
        long pages = total == 0 ? 0 : ((total - 1) / query.getSize()) + 1;
        return new FulfillmentPage<>(ordered, total, query.getPage(), query.getSize(), pages);
    }

    @Transactional(readOnly = true)
    public ShippingOrderView get(String shippingNo) {
        ShippingOrder order = require(shippingNo);
        return shipping.getForUser(order.targetUserId(), order.shippingNo());
    }

    @Transactional(readOnly = true)
    public ShippingTrackingView tracking(String shippingNo) {
        ShippingOrder order = require(shippingNo);
        return tracking.getForUser(order.targetUserId(), order.shippingNo());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView retry(String shippingNo, long operatorId, String note) {
        ShippingOrder order = lock(shippingNo);
        if (order.status() != ShippingStatus.FAILED || order.fulfillmentNo() == null) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        FulfillmentTaskView task = tasks.get(order.fulfillmentNo());
        if (task.status() != FulfillmentStatus.QUARANTINED) throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        taskOperations.retryQuarantined(order.fulfillmentNo(), operator(operatorId), safeNote(note));
        projector.projectFulfillmentState(order.fulfillmentNo());
        return get(order.shippingNo());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShippingOrderView terminate(String shippingNo, long operatorId, String note) {
        ShippingOrder order = lock(shippingNo);
        if (order.fulfillmentNo() == null || order.status() == ShippingStatus.SHIPPED
                || order.status() == ShippingStatus.IN_TRANSIT || order.status() == ShippingStatus.DELIVERED
                || order.status() == ShippingStatus.TERMINATED) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        FulfillmentTaskView task = tasks.get(order.fulfillmentNo());
        if (task.status() == FulfillmentStatus.PROCESSING || task.status() == FulfillmentStatus.SUCCEEDED
                || task.status() == FulfillmentStatus.TERMINATED) {
            throw error(ShippingErrorCode.SHIPPING_STATE_CONFLICT);
        }
        taskOperations.terminate(order.fulfillmentNo(), operator(operatorId), safeNote(note));
        projector.projectFulfillmentState(order.fulfillmentNo());
        return get(order.shippingNo());
    }

    private ShippingOrder require(String shippingNo) {
        String no = requiredNo(shippingNo);
        return orders.findByShippingNo(no).orElseThrow(() -> error(ShippingErrorCode.SHIPPING_NOT_FOUND));
    }

    private ShippingOrder lock(String shippingNo) {
        String no = requiredNo(shippingNo);
        return orders.lockByShippingNo(no).orElseThrow(() -> error(ShippingErrorCode.SHIPPING_NOT_FOUND));
    }

    private String requiredNo(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 64) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return value.trim();
    }

    private long operator(long value) {
        if (value <= 0) throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        return value;
    }

    private String safeNote(String value) {
        if (value == null || value.isBlank()) return null;
        String note = value.trim();
        if (UNSAFE_NOTE.matcher(note).find() || !SAFE_NOTE.matcher(note).matches()) {
            throw error(ShippingErrorCode.SHIPPING_REQUEST_INVALID);
        }
        return note.substring(0, Math.min(note.length(), 500));
    }

    private ShippingDomainException error(ShippingErrorCode code) { return new ShippingDomainException(code); }
}
