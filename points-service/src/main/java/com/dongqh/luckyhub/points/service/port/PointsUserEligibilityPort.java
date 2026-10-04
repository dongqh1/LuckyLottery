package com.dongqh.luckyhub.points.service.port;

/**
 * Confirms user eligibility through an external boundary, without coupling
 * the points module to a user database or identity implementation.
 */
@FunctionalInterface
public interface PointsUserEligibilityPort {
    boolean isEnabled(long userId);
}
