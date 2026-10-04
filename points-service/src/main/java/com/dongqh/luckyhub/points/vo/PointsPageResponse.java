package com.dongqh.luckyhub.points.vo;

import java.util.List;

public record PointsPageResponse<T>(
        List<T> records,
        long total,
        long page,
        long size,
        long pages
) {
}
