package com.dongqh.membershipservice.membership.listener;

import com.dongqh.hmapi.dto.PaySuccessMessage;
import com.dongqh.membershipservice.membership.config.MembershipPayMessagingConfiguration;
import com.dongqh.membershipservice.membership.service.MembershipRewardGrantService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "membership.messaging", name = "enabled", havingValue = "true")
public class MembershipPaySuccessListener {
    private final MembershipRewardGrantService grants;

    public MembershipPaySuccessListener(MembershipRewardGrantService grants) {
        this.grants = grants;
    }

    @RabbitListener(queues = MembershipPayMessagingConfiguration.QUEUE)
    public void listen(PaySuccessMessage message) {
        grants.grantPaidPurchase(message);
    }
}
