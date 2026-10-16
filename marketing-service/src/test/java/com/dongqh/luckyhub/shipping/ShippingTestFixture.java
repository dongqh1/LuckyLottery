package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.worker.fulfillment.FulfillmentWorker;
import com.dongqh.luckyhub.domain.dto.activity.AddActivityPrizeCommand;
import com.dongqh.luckyhub.domain.dto.activity.CreateActivityCommand;
import com.dongqh.luckyhub.service.activity.ActivityPrizeService;
import com.dongqh.luckyhub.service.activity.ActivityService;
import com.dongqh.luckyhub.domain.context.auth.LoginContext;
import com.dongqh.luckyhub.domain.model.auth.LoginPrincipal;
import com.dongqh.luckyhub.service.benefit.LotteryRewardProjectionService;
import com.dongqh.luckyhub.service.benefit.BenefitQueryService;
import com.dongqh.luckyhub.domain.simulator.integration.controller.SimulatorAdminController;
import com.dongqh.luckyhub.domain.algorithm.lottery.DrawRandomSource;
import com.dongqh.luckyhub.domain.dto.lottery.DrawCommand;
import com.dongqh.luckyhub.mapper.lottery.MessageOutboxMapper;
import com.dongqh.luckyhub.messaging.lottery.event.DrawEventEnvelope;
import com.dongqh.luckyhub.service.lottery.LotteryService;
import com.dongqh.luckyhub.service.lottery.MessageConsumeService;
import com.dongqh.luckyhub.domain.dto.order.CreateCashOrderCommand;
import com.dongqh.luckyhub.service.order.CashOrderService;
import com.dongqh.luckyhub.domain.dto.payment.CreatePaymentCommand;
import com.dongqh.luckyhub.domain.dto.payment.PaymentCallbackCommand;
import com.dongqh.luckyhub.enums.payment.PaymentResult;
import com.dongqh.luckyhub.service.payment.PaymentService;
import com.dongqh.luckyhub.domain.dto.points.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.domain.dto.points.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.service.points.PointsAccountService;
import com.dongqh.luckyhub.service.points.PointsRedemptionService;
import com.dongqh.luckyhub.domain.dto.prize.CreatePrizeCommand;
import com.dongqh.luckyhub.enums.prize.PrizeLevel;
import com.dongqh.luckyhub.enums.prize.PrizeType;
import com.dongqh.luckyhub.service.prize.PrizeService;
import com.dongqh.luckyhub.domain.dto.reward.CreateRewardDefinitionCommand;
import com.dongqh.luckyhub.enums.reward.RewardType;
import com.dongqh.luckyhub.service.reward.RewardDefinitionService;
import com.dongqh.luckyhub.domain.dto.shipping.SimulateTrackingEventCommand;
import com.dongqh.luckyhub.enums.shipping.TrackingEventType;
import com.dongqh.luckyhub.service.shipping.ShippingAdminService;
import com.dongqh.luckyhub.service.shipping.ShippingProjectionWorker;
import com.dongqh.luckyhub.service.shipping.ShippingQueryService;
import com.dongqh.luckyhub.service.shipping.PhysicalClaimService;
import com.dongqh.luckyhub.domain.dto.shipping.ClaimPhysicalBenefitCommand;
import com.dongqh.luckyhub.domain.crypto.shipping.AddressCipher;
import com.dongqh.luckyhub.domain.crypto.shipping.LogisticsCallbackSigner;
import com.dongqh.luckyhub.service.shipping.LogisticsCallbackService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

abstract class ShippingTestFixture extends Task5ShippingTestFixture {
    private final String privacySuffix = UUID.randomUUID().toString().substring(0, 8);
    protected final String receiverProbe = "顾隐私T8" + privacySuffix;
    protected final String phoneProbe = "139" + String.format("%08d",
            Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
    protected final String detailProbe = "Task8独特探针海创园八号楼" + privacySuffix + "室";

    @Autowired protected CashOrderService cashOrders;
    @Autowired protected PaymentService payments;
    @Autowired protected FulfillmentWorker worker;
    @Autowired protected SimulatorAdminController simulator;
    @Autowired protected ShippingProjectionWorker shippingProjector;
    @Autowired protected ShippingQueryService shippingQueries;
    @Autowired protected ShippingAdminService shippingAdmin;
    @Autowired protected PointsRedemptionService redemptions;
    @Autowired protected PointsAccountService points;
    @Autowired protected ActivityService activities;
    @Autowired protected ActivityPrizeService activityPrizes;
    @Autowired protected RewardDefinitionService rewards;
    @Autowired protected PrizeService prizes;
    @Autowired protected LotteryService lottery;
    @Autowired protected MessageConsumeService messageConsumer;
    @Autowired protected LotteryRewardProjectionService benefitProjector;
    @Autowired protected PhysicalClaimService claims;
    @Autowired protected BenefitQueryService benefitQueries;
    @Autowired protected AddressCipher addressCipher;
    @Autowired protected LogisticsCallbackSigner callbackSigner;
    @Autowired protected LogisticsCallbackService logisticsCallbacks;
    @Autowired protected MessageOutboxMapper outboxes;
    @Autowired protected ObjectMapper json;
    @MockitoBean protected DrawRandomSource drawRandom;

    private final List<Long> lotteryActivityIds = new ArrayList<>();
    private final List<Long> lotteryPrizeIds = new ArrayList<>();
    private final List<Long> rewardIds = new ArrayList<>();
    private final List<Long> lotteryUserIds = new ArrayList<>();
    private final List<String> lotteryDrawRequestIds = new ArrayList<>();
    private final List<Long> task8ShippingOrderIds = new ArrayList<>();

    protected long createPrivateAddress(long userId) {
        long addressId = addresses.create(userId, new com.dongqh.luckyhub.domain.dto.shipping.CreateShippingAddressCommand(
                receiverProbe, phoneProbe, "浙江省", "杭州市", "余杭区", detailProbe, false)).id();
        stubHmallAddress(addressId, receiverProbe, phoneProbe, detailProbe);
        return addressId;
    }

    protected CashFlow paidCashFlow() {
        return completeCashFlow(prepareCashFlow());
    }

    protected PreparedCashFlow prepareCashFlow() {
        long userId = createUser();
        long addressId = createPrivateAddress(userId);
        long skuId = createPhysicalSku(true, false);
        String orderNo = unique("TASK8-CASH");
        String paymentNo = unique("TASK8-PAY");
        cashOrders.create(userId, new CreateCashOrderCommand(orderNo, skuId, 1, null, addressId));
        var payment = payments.create(userId, new CreatePaymentCommand(paymentNo, orderNo));
        var callback = new PaymentCallbackCommand(paymentNo, PaymentResult.SUCCESS, null,
                payments.signForSimulation(paymentNo, PaymentResult.SUCCESS, payment.amountCent()));
        long sourceId = jdbc.queryForObject("SELECT id FROM mall_order WHERE order_no=?", Long.class, orderNo);
        return new PreparedCashFlow(userId, addressId, orderNo, paymentNo, sourceId, callback);
    }

    protected CashFlow completeCashFlow(PreparedCashFlow prepared) {
        payments.callback(prepared.paymentCallback());
        return completedCashFlow(prepared);
    }

    protected CashFlow completedCashFlow(PreparedCashFlow prepared) {
        var source = cashOrders.get(prepared.userId(), prepared.orderNo());
        task8ShippingOrderIds.add(source.shippingOrderId());
        String fulfillmentNo = "LOGISTICS-" + source.shippingOrderId();
        trackFulfillment(fulfillmentNo);
        return new CashFlow(prepared.userId(), prepared.orderNo(), prepared.paymentNo(), prepared.sourceId(),
                source.shippingOrderId(), source.shippingNo(), fulfillmentNo, prepared.paymentCallback());
    }

    protected PointsFlow pointsFlow() {
        return completePointsFlow(preparePointsFlow());
    }

    protected PreparedPointsFlow preparePointsFlow() {
        long userId = createUser();
        long skuId = createPhysicalSku(false, true);
        long addressId = createPrivateAddress(userId);
        points.adjust(new AdminPointsAdjustmentCommand(userId, 1_000L, unique("TASK8-SEED"), "验收入账"));
        String redemptionNo = unique("TASK8-POINTS");
        return new PreparedPointsFlow(userId, skuId, addressId, redemptionNo);
    }

    protected PointsFlow completePointsFlow(PreparedPointsFlow prepared) {
        redemptions.create(prepared.userId(),
                new CreatePointsRedemptionCommand(prepared.redemptionNo(), prepared.skuId(), 1, prepared.addressId()));
        return completedPointsFlow(prepared);
    }

    protected PointsFlow completedPointsFlow(PreparedPointsFlow prepared) {
        var source = redemptions.get(prepared.userId(), prepared.redemptionNo());
        long sourceId = jdbc.queryForObject("SELECT id FROM points_redemption_order WHERE redemption_no=?",
                Long.class, prepared.redemptionNo());
        task8ShippingOrderIds.add(source.shippingOrderId());
        String fulfillmentNo = "LOGISTICS-" + source.shippingOrderId();
        trackFulfillment(fulfillmentNo);
        return new PointsFlow(prepared.userId(), prepared.redemptionNo(), sourceId, source.shippingOrderId(),
                source.shippingNo(), fulfillmentNo);
    }

    protected LotteryFlow lotteryFlow() throws Exception {
        return completeLotteryFlow(prepareLotteryFlow());
    }

    protected PreparedLotteryFlow prepareLotteryFlow() throws Exception {
        long userId = createUser();
        lotteryUserIds.add(userId);
        long skuId = createPhysicalSku(true, false);
        long addressId = createPrivateAddress(userId);
        String suffix = UUID.randomUUID().toString();
        var reward = rewards.create(new CreateRewardDefinitionCommand(
                "TASK8-R-" + suffix, "验收实物奖励", RewardType.PRODUCT, skuId, 1L, null));
        rewardIds.add(reward.id());
        var prize = prizes.create(new CreatePrizeCommand(
                "验收实物奖品", PrizeType.PHYSICAL, PrizeLevel.FIRST,
                "https://cdn.example/task8.png", null, false, reward.id()));
        lotteryPrizeIds.add(prize.id());
        LoginContext.set(new LoginPrincipal(userId, "task8-" + suffix, "task8-session"));
        try {
            var activity = activities.create(new CreateActivityCommand(
                    "验收抽奖活动", null, LocalDateTime.now().minusHours(1),
                    LocalDateTime.now().plusHours(1), 1, 0));
            lotteryActivityIds.add(activity.id());
            activityPrizes.add(activity.id(), new AddActivityPrizeCommand(prize.id(), 100, 1, 0));
            activities.publish(activity.id());
            when(drawRandom.nextLong(anyLong())).thenReturn(0L);
            String drawRequestId = UUID.randomUUID().toString();
            lotteryDrawRequestIds.add(drawRequestId);
            var draw = lottery.draw(new DrawCommand(drawRequestId, activity.id(), 1));
            long benefitId = draw.results().get(0).benefitId();
            assertNoShippingAtWin(benefitId);
            String payload = jdbc.queryForObject(
                    "SELECT payload FROM message_outbox WHERE payload ->> '$.requestId'=? AND event_type='PRIZE_FULFILLMENT_REQUESTED'",
                    String.class, drawRequestId);
            messageConsumer.consume(json.readValue(payload, DrawEventEnvelope.class));
            benefitProjector.project(benefitId);
            String claimRequestId = UUID.randomUUID().toString();
            return new PreparedLotteryFlow(userId, addressId, benefitId, claimRequestId);
        } finally {
            LoginContext.clear();
        }
    }

    protected LotteryFlow completeLotteryFlow(PreparedLotteryFlow prepared) {
        var shipping = claims.claim(prepared.userId(), prepared.benefitId(),
                new ClaimPhysicalBenefitCommand(prepared.claimRequestId(), prepared.addressId()));
        return completedLotteryFlow(prepared, shipping.id(), shipping.shippingNo(), shipping.fulfillmentNo());
    }

    protected LotteryFlow completedLotteryFlow(PreparedLotteryFlow prepared) {
        var row = jdbc.queryForMap("""
                SELECT id,shipping_no,fulfillment_no FROM shipping_order
                WHERE source_type='LOTTERY_BENEFIT' AND source_id=?
                """, Long.toString(prepared.benefitId()));
        return completedLotteryFlow(prepared, ((Number) row.get("id")).longValue(),
                (String) row.get("shipping_no"), (String) row.get("fulfillment_no"));
    }

    private LotteryFlow completedLotteryFlow(PreparedLotteryFlow prepared, long shippingOrderId,
                                              String shippingNo, String fulfillmentNo) {
        task8ShippingOrderIds.add(shippingOrderId);
        trackFulfillment(fulfillmentNo);
        return new LotteryFlow(prepared.userId(), prepared.benefitId(), prepared.claimRequestId(), shippingOrderId,
                shippingNo, fulfillmentNo);
    }

    private void assertNoShippingAtWin(long benefitId) {
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_order WHERE source_type='LOTTERY_BENEFIT' AND source_id=?",
                Integer.class, Long.toString(benefitId))).isZero();
    }

    protected void shipAndDeliver(long shippingOrderId, String fulfillmentNo) {
        worker.runBatch();
        shippingProjector.projectOne(shippingOrderId);
        LocalDateTime base = LocalDateTime.now().minusMinutes(4).truncatedTo(ChronoUnit.MILLIS);
        emit(fulfillmentNo, TrackingEventType.PICKED_UP, base);
        emit(fulfillmentNo, TrackingEventType.IN_TRANSIT, base.plusMinutes(1));
        emit(fulfillmentNo, TrackingEventType.OUT_FOR_DELIVERY, base.plusMinutes(2));
        emit(fulfillmentNo, TrackingEventType.DELIVERED, base.plusMinutes(3));
    }

    private void emit(String fulfillmentNo, TrackingEventType type, LocalDateTime eventTime) {
        simulator.trackingEvent(fulfillmentNo,
                new SimulateTrackingEventCommand(type, eventTime, "杭州物流节点", "安全物流摘要"));
    }

    @AfterEach
    void cleanTask8Tracking() {
        task8ShippingOrderIds.forEach(id -> jdbc.update(
                "DELETE FROM shipping_tracking_event WHERE shipping_order_id=?", id));
        task8ShippingOrderIds.forEach(id -> jdbc.update(
                "DELETE r FROM shipping_callback_receipt r JOIN shipping_order o ON o.waybill_no=r.waybill_no WHERE o.id=?", id));
        lotteryDrawRequestIds.forEach(id -> jdbc.update("DELETE FROM message_consume_record WHERE event_id IN (SELECT event_id FROM message_outbox WHERE payload ->> '$.requestId'=?)", id));
        lotteryDrawRequestIds.forEach(id -> jdbc.update("DELETE FROM message_outbox WHERE payload ->> '$.requestId'=?", id));
        lotteryUserIds.forEach(id -> jdbc.update("DELETE FROM user_benefit WHERE user_id=?", id));
        lotteryUserIds.forEach(id -> jdbc.update("DELETE FROM lottery_draw_record WHERE user_id=?", id));
        lotteryUserIds.forEach(id -> jdbc.update("DELETE FROM lottery_draw_order WHERE user_id=?", id));
        lotteryActivityIds.forEach(id -> jdbc.update("DELETE FROM marketing_activity_prize WHERE activity_id=?", id));
        lotteryActivityIds.forEach(id -> jdbc.update("DELETE FROM marketing_activity WHERE id=?", id));
        lotteryPrizeIds.forEach(id -> jdbc.update("DELETE FROM marketing_prize WHERE id=?", id));
        rewardIds.forEach(id -> jdbc.update("DELETE FROM reward_definition WHERE id=?", id));
        lotteryActivityIds.clear(); lotteryPrizeIds.clear(); rewardIds.clear();
        lotteryUserIds.clear(); lotteryDrawRequestIds.clear();
        task8ShippingOrderIds.clear();
    }

    protected record PreparedCashFlow(long userId, long addressId, String orderNo, String paymentNo,
                                      long sourceId, PaymentCallbackCommand paymentCallback) { }

    protected record CashFlow(long userId, String orderNo, String paymentNo, long sourceId,
                              long shippingOrderId, String shippingNo,
                              String fulfillmentNo, PaymentCallbackCommand paymentCallback) {
    }

    protected record PreparedPointsFlow(long userId, long skuId, long addressId, String redemptionNo) { }

    protected record PointsFlow(long userId, String redemptionNo, long sourceId, long shippingOrderId,
                                String shippingNo, String fulfillmentNo) { }

    protected record PreparedLotteryFlow(long userId, long addressId, long benefitId,
                                         String claimRequestId) { }

    protected record LotteryFlow(long userId, long benefitId, String claimRequestId,
                                 long shippingOrderId, String shippingNo, String fulfillmentNo) { }
}
