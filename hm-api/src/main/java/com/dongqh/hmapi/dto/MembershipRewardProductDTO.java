package com.dongqh.hmapi.dto;

public record MembershipRewardProductDTO(Long id, String productCode, String membershipLevel,
                                         Integer durationDays, Integer status) {
}
