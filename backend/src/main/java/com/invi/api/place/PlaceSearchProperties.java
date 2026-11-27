package com.invi.api.place;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Guards for the Kakao-backed place search. Kakao's free keyword-search quota is 100,000 calls a
 * day per app and the paid API is off (calls past the quota fail, nothing is billed), so the risk
 * is one account or a crowd of new accounts using up the day for everyone:
 * <ul>
 *   <li>{@code perMember} searches per {@code memberWindow} for each logged-in account;</li>
 *   <li>{@code dailyCalls} Kakao calls per Korean calendar day across the app, kept well under the quota;</li>
 *   <li>identical searches are answered from a cache for {@code cacheTtl} ({@code cacheSize} entries).</li>
 * </ul>
 */
@ConfigurationProperties("invi.place-search")
public record PlaceSearchProperties(
        @DefaultValue("30") int perMember,
        @DefaultValue("PT10M") Duration memberWindow,
        @DefaultValue("50000") int dailyCalls,
        @DefaultValue("PT1H") Duration cacheTtl,
        @DefaultValue("1000") int cacheSize,
        @DefaultValue("10000") int maxTrackedMembers) {
    public PlaceSearchProperties {
        if (perMember < 1 || dailyCalls < 1 || cacheSize < 0 || maxTrackedMembers < 100) {
            throw new IllegalArgumentException("Invalid place search limits.");
        }
        if (memberWindow == null || memberWindow.isNegative() || memberWindow.isZero()
                || cacheTtl == null || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("Place search windows must be positive.");
        }
    }
}
