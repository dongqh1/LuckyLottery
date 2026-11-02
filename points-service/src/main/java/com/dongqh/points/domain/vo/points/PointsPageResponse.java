package com.dongqh.points.domain.vo.points;

import java.util.List;

public record PointsPageResponse<T>(
        List<T> records,
        long total,
        long page,
        long size,
        long pages
) {
}
