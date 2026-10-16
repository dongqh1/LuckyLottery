package com.dongqh.luckyhub.domain.simulator.integration;
import com.dongqh.luckyhub.enums.fulfillment.FulfillmentType;import jakarta.validation.constraints.*;
public record ConfigureSimulatorFailureRuleCommand(@NotNull FulfillmentType fulfillmentType,@NotNull SimulatorFailureMode failureMode,@Min(0) @Max(10000) int count){}
