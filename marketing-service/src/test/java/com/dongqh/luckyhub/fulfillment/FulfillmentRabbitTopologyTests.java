package com.dongqh.luckyhub.fulfillment;

import com.dongqh.luckyhub.config.fulfillment.FulfillmentMessagingConfiguration;
import com.dongqh.luckyhub.messaging.fulfillment.rabbit.FulfillmentRabbitTopology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentRabbitTopologyTests {
    @Test void declaresDurableDirectQueueAndDeadLetterTopology(){
        var resources=new FulfillmentMessagingConfiguration().fulfillmentRabbitDeclarables().getDeclarables();
        assertThat(resources).filteredOn(DirectExchange.class::isInstance).extracting(x->((DirectExchange)x).getName()).containsExactlyInAnyOrder(FulfillmentRabbitTopology.EXCHANGE,FulfillmentRabbitTopology.DEAD_LETTER_EXCHANGE);
        Queue queue=resources.stream().filter(Queue.class::isInstance).map(Queue.class::cast).filter(q->q.getName().equals(FulfillmentRabbitTopology.EXECUTE_QUEUE)).findFirst().orElseThrow();
        assertThat(queue.isDurable()).isTrue();assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange",FulfillmentRabbitTopology.DEAD_LETTER_EXCHANGE);
        assertThat(resources).filteredOn(Binding.class::isInstance).extracting(x->((Binding)x).getExchange()+":"+((Binding)x).getRoutingKey()).contains(FulfillmentRabbitTopology.EXCHANGE+":"+FulfillmentRabbitTopology.ROUTING_KEY,FulfillmentRabbitTopology.DEAD_LETTER_EXCHANGE+":"+FulfillmentRabbitTopology.ROUTING_KEY);
    }
}
