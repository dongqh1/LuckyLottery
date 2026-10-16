package com.dongqh.luckyhub.domain.vo.points;

import java.time.LocalDateTime;

public record PointsAccountView(Long userId, Long balance, LocalDateTime updatedAt) {
}
