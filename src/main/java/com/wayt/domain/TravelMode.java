package com.wayt.domain;

public enum TravelMode {
    TRANSIT,
    WALK,
    CAR,
    BICYCLE,
    UNKNOWN;

    public TravelMode resolved() {
        return this == UNKNOWN ? TRANSIT : this;
    }
}
