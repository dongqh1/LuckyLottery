package com.dongqh.luckyhub.points.service.port;

public record PointsItemSnapshot(
        Long id,
        String name,
        Integer priceCent,
        Integer stock,
        String imageUrl,
        Integer status
) {
}
