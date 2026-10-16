package com.dongqh.lotteryservice.outbox;

import com.dongqh.hmapi.messaging.FulfillmentStatusRabbitTopology;
import com.dongqh.lotteryservice.outbox.config.LotteryOutboxRabbitConfiguration;
import com.dongqh.lotteryservice.outbox.event.DrawConfirmedEvent;
import com.dongqh.lotteryservice.outbox.event.DrawEventEnvelope;
import com.dongqh.lotteryservice.outbox.event.DrawEventType;
import com.dongqh.lotteryservice.outbox.mapper.MessageOutboxMapper;
import com.dongqh.lotteryservice.outbox.publisher.DrawEventPublisher;
import com.dongqh.lotteryservice.outbox.publisher.LotteryRabbitTopology;
import com.dongqh.lotteryservice.outbox.publisher.RabbitDrawEventPublisher;
import com.dongqh.lotteryservice.outbox.service.OutboxRelayService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitPublisherAndOptInTests {
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();

    @Test
    void topologyAndConverterAreAbsentUntilLotteryOptIn() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(LotteryOutboxRabbitConfiguration.class);
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(Declarables.class);
            assertThat(context).doesNotHaveBean(MessageConverter.class);
        });
        runner.withPropertyValues("luckyhub.lottery.outbox.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(Declarables.class);
            assertThat(context).hasSingleBean(MessageConverter.class);
        });
    }

    @Test
    void topologyKeepsDurableQueuesDeadLettersAndFulfillmentStatusBindings() {
        var resources = new LotteryOutboxRabbitConfiguration().lotteryRabbitDeclarables()
                .getDeclarables();
        assertThat(resources).filteredOn(TopicExchange.class::isInstance)
                .extracting(item -> ((TopicExchange) item).getName())
                .containsExactlyInAnyOrder(LotteryRabbitTopology.EVENT_EXCHANGE,
                        LotteryRabbitTopology.DEAD_LETTER_EXCHANGE);
        assertThat(resources).filteredOn(DirectExchange.class::isInstance)
                .extracting(item -> ((DirectExchange) item).getName())
                .containsExactlyInAnyOrder(FulfillmentStatusRabbitTopology.EXCHANGE,
                        FulfillmentStatusRabbitTopology.DEAD_LETTER_EXCHANGE);
        Queue main = resources.stream().filter(Queue.class::isInstance).map(Queue.class::cast)
                .filter(queue -> queue.getName().equals(LotteryRabbitTopology.EVENT_QUEUE))
                .findFirst().orElseThrow();
        assertThat(main.isDurable()).isTrue();
        assertThat(main.getArguments()).containsEntry("x-dead-letter-exchange",
                LotteryRabbitTopology.DEAD_LETTER_EXCHANGE);
        Queue fulfillmentStatusQueue = resources.stream().filter(Queue.class::isInstance).map(Queue.class::cast)
                .filter(queue -> queue.getName().equals(FulfillmentStatusRabbitTopology.QUEUE))
                .findFirst().orElseThrow();
        assertThat(fulfillmentStatusQueue.isDurable()).isTrue();
        assertThat(fulfillmentStatusQueue.getArguments())
                .containsEntry("x-dead-letter-exchange", FulfillmentStatusRabbitTopology.DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", FulfillmentStatusRabbitTopology.DEAD_LETTER_ROUTING_KEY);
        Queue fulfillmentStatusDeadLetterQueue = resources.stream().filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .filter(queue -> queue.getName().equals(FulfillmentStatusRabbitTopology.DEAD_LETTER_QUEUE))
                .findFirst().orElseThrow();
        assertThat(fulfillmentStatusDeadLetterQueue.isDurable()).isTrue();
        assertThat(resources).filteredOn(Binding.class::isInstance)
                .extracting(item -> ((Binding) item).getExchange() + ":" + ((Binding) item).getRoutingKey())
                .containsExactlyInAnyOrder(
                        LotteryRabbitTopology.EVENT_EXCHANGE + ":" + LotteryRabbitTopology.DRAW_CONFIRMED_KEY,
                        LotteryRabbitTopology.EVENT_EXCHANGE + ":" + LotteryRabbitTopology.DRAW_RELEASE_REQUESTED_KEY,
                        LotteryRabbitTopology.EVENT_EXCHANGE + ":" + LotteryRabbitTopology.PRIZE_FULFILLMENT_REQUESTED_KEY,
                        LotteryRabbitTopology.DEAD_LETTER_EXCHANGE + ":#",
                        FulfillmentStatusRabbitTopology.EXCHANGE + ":" + FulfillmentStatusRabbitTopology.ROUTING_KEY,
                        FulfillmentStatusRabbitTopology.DEAD_LETTER_EXCHANGE + ":"
                                + FulfillmentStatusRabbitTopology.DEAD_LETTER_ROUTING_KEY);
    }

    @Test
    void relayPublisherAndSchedulerBeansAreAbsentByDefaultAndPresentWithOptIn() {
        var relay = new ApplicationContextRunner()
                .withBean(MessageOutboxMapper.class, () -> mock(MessageOutboxMapper.class))
                .withBean(DrawEventPublisher.class, () -> mock(DrawEventPublisher.class))
                .withBean(ObjectMapper.class, () -> json)
                .withUserConfiguration(OutboxRelayService.class);
        relay.run(context -> assertThat(context).doesNotHaveBean(OutboxRelayService.class));
        relay.withPropertyValues("luckyhub.lottery.outbox.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(OutboxRelayService.class));

        var publisher = new ApplicationContextRunner()
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
                .withUserConfiguration(RabbitDrawEventPublisher.class);
        publisher.run(context -> assertThat(context).doesNotHaveBean(RabbitDrawEventPublisher.class));
        publisher.withPropertyValues("luckyhub.lottery.outbox.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(RabbitDrawEventPublisher.class));

        relay.run(context -> assertThat(context).doesNotHaveBean(OutboxRelayService.class));
        relay.withPropertyValues("luckyhub.lottery.outbox.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(OutboxRelayService.class));
    }

    @Test
    void publisherUsesStableRoutingKeysAndRejectsNackReturnAndTimeout() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
        DrawEventEnvelope event = event(DrawEventType.DRAW_CONFIRMED);
        new RabbitDrawEventPublisher(template, Duration.ofSeconds(1)).publish(event);
        verify(template).convertAndSend(eq(LotteryRabbitTopology.EVENT_EXCHANGE),
                eq(LotteryRabbitTopology.DRAW_CONFIRMED_KEY), eq(event),
                org.mockito.ArgumentMatchers.<CorrelationData>argThat(data -> data.getId().equals(event.eventId().toString())));
        assertThat(LotteryRabbitTopology.routingKey(DrawEventType.DRAW_RELEASE_REQUESTED))
                .isEqualTo("lottery.draw.release-requested");
        assertThat(LotteryRabbitTopology.routingKey(DrawEventType.PRIZE_FULFILLMENT_REQUESTED))
                .isEqualTo("lottery.prize.fulfillment-requested");

        RabbitTemplate nack = mock(RabbitTemplate.class);
        doAnswer(call -> {
            ((CorrelationData) call.getArgument(3)).getFuture()
                    .complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(nack).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
        assertThatThrownBy(() -> new RabbitDrawEventPublisher(nack, Duration.ofSeconds(1)).publish(event))
                .isInstanceOf(AmqpException.class).hasMessageContaining("NACK");

        RabbitTemplate returned = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                    312, "NO_ROUTE", LotteryRabbitTopology.EVENT_EXCHANGE,
                    LotteryRabbitTopology.DRAW_CONFIRMED_KEY));
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(returned).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
        assertThatThrownBy(() -> new RabbitDrawEventPublisher(returned, Duration.ofSeconds(1)).publish(event))
                .isInstanceOf(AmqpException.class).hasMessageContaining("unroutable");

        assertThatThrownBy(() -> new RabbitDrawEventPublisher(mock(RabbitTemplate.class),
                Duration.ofMillis(1)).publish(event))
                .isInstanceOf(AmqpException.class).hasMessageContaining("timed out");
    }

    @Test
    void publisherRequiresPositiveConfirmTimeout() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        assertThatThrownBy(() -> new RabbitDrawEventPublisher(template, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("confirmTimeout");
        assertThatThrownBy(() -> new RabbitDrawEventPublisher(template, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("confirmTimeout");
        assertThatThrownBy(() -> new RabbitDrawEventPublisher(template, (Duration) null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("confirmTimeout");
    }

    @Test
    void relayKeepsLegacyIntervals() throws Exception {
        Scheduled schedule = OutboxRelayService.class.getMethod("relayOutbox").getAnnotation(Scheduled.class);
        assertThat(schedule.fixedDelayString()).isEqualTo("${luckyhub.lottery.outbox-interval:5s}");
        assertThat(schedule.initialDelayString()).isEqualTo("${luckyhub.lottery.outbox-initial-delay:60s}");
    }

    private DrawEventEnvelope event(DrawEventType type) {
        return DrawEventEnvelope.create(type, "request-1", 1L, 2L, 3L,
                LocalDateTime.of(2026, 9, 16, 20, 0),
                new DrawConfirmedEvent(1, LocalDate.of(2026, 9, 16)), json);
    }
}
