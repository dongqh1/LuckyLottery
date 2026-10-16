package com.dongqh.luckyhub.lottery.messaging;

import com.dongqh.luckyhub.config.lottery.LotteryMessagingConfiguration;
import com.dongqh.luckyhub.config.lottery.MessagingProperties;
import com.dongqh.luckyhub.messaging.lottery.rabbit.LotteryRabbitTopology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitTopologyConfigurationTests {

    private final LotteryMessagingConfiguration configuration = new LotteryMessagingConfiguration();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LotteryMessagingConfiguration.class)
            .withPropertyValues(
                    "luckyhub.messaging.logical-consumer-name=test-consumer",
                    "luckyhub.messaging.publisher-confirm-timeout=5s",
                    "luckyhub.messaging.outbox-lease=30s");

    @Test
    void doesNotCreateLotteryRabbitInfrastructureWhenMessagingIsDisabled() {
        contextRunner.withPropertyValues("luckyhub.messaging.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(LotteryMessagingConfiguration.class);
                    assertThat(context).hasSingleBean(MessagingProperties.class);
                    assertThat(context).doesNotHaveBean(MessageConverter.class);
                    assertThat(context).doesNotHaveBean(Declarables.class);
                });
    }

    @Test
    void createsLotteryRabbitInfrastructureWhenMessagingSwitchIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LotteryMessagingConfiguration.class);
            assertThat(context).hasSingleBean(MessageConverter.class);
            assertThat(context).hasSingleBean(Declarables.class);
        });
    }

    @Test
    void declaresDurableLotteryTopologyWithDeadLetterRouting() {
        Declarables declarables = configuration.lotteryRabbitDeclarables();
        List<Object> resources = declarables.getDeclarables().stream()
                .map(declarable -> (Object) declarable)
                .toList();

        assertThat(resources).filteredOn(TopicExchange.class::isInstance)
                .extracting(resource -> ((TopicExchange) resource).getName())
                .containsExactlyInAnyOrder(
                        LotteryRabbitTopology.EVENT_EXCHANGE,
                        LotteryRabbitTopology.DEAD_LETTER_EXCHANGE);

        Queue eventQueue = resources.stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .filter(queue -> queue.getName().equals(LotteryRabbitTopology.EVENT_QUEUE))
                .findFirst()
                .orElseThrow();
        assertThat(eventQueue.isDurable()).isTrue();
        assertThat(eventQueue.getArguments())
                .containsEntry("x-dead-letter-exchange", LotteryRabbitTopology.DEAD_LETTER_EXCHANGE);

        Queue deadLetterQueue = resources.stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .filter(queue -> queue.getName().equals(LotteryRabbitTopology.DEAD_LETTER_QUEUE))
                .findFirst()
                .orElseThrow();
        assertThat(deadLetterQueue.isDurable()).isTrue();

        assertThat(resources).filteredOn(Binding.class::isInstance)
                .extracting(resource -> ((Binding) resource).getExchange() + ":" + ((Binding) resource).getRoutingKey())
                .containsExactlyInAnyOrder(
                        LotteryRabbitTopology.EVENT_EXCHANGE + ":" + LotteryRabbitTopology.DRAW_CONFIRMED_KEY,
                        LotteryRabbitTopology.EVENT_EXCHANGE + ":" + LotteryRabbitTopology.DRAW_RELEASE_REQUESTED_KEY,
                        LotteryRabbitTopology.EVENT_EXCHANGE + ":" + LotteryRabbitTopology.PRIZE_FULFILLMENT_REQUESTED_KEY,
                        LotteryRabbitTopology.DEAD_LETTER_EXCHANGE + ":#");
    }

    @Test
    void usesBoot4JacksonJsonMessageConverter() {
        assertThat(configuration.rabbitMessageConverter())
                .isInstanceOf(JacksonJsonMessageConverter.class);
    }
}
