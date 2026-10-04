package com.dongqh.luckyhub.points.service.port;

import java.util.Optional;

public interface PointsItemCatalogPort {
    Optional<PointsItemSnapshot> getItem(long itemId);
}
