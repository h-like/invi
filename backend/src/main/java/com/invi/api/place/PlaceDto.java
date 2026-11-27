package com.invi.api.place;

/** One venue from the map provider, as the editor stores it into the map block. */
public record PlaceDto(
        String id,
        String name,
        String category,
        String roadAddress,
        String address,
        String phone,
        double lat,
        double lng,
        String placeUrl) {}
