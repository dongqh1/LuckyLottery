package com.dongqh.luckyhub.domain.simulator.integration;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentType;
public interface SimulatorFailureRuleService {
 void configure(FulfillmentType type, SimulatorFailureMode mode, int count);
 SimulatorFailureMode consume(FulfillmentType type);
}
