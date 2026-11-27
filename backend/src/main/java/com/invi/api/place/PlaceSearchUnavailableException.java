package com.invi.api.place;

/** No map API key configured, or the map provider refused or failed the request. */
public class PlaceSearchUnavailableException extends RuntimeException {

    public PlaceSearchUnavailableException(String message) {
        super(message);
    }

    public PlaceSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
