package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.config.ClientConfig;

public final class WindDirection {
    public static final double DEFAULT_DEGREES = 125.0D;

    private WindDirection() {
    }

    public static double degrees() {
        return ClientConfig.WIND_DIRECTION_DEGREES.getAsDouble();
    }

    public static float x() {
        return (float) xDouble();
    }

    public static float z() {
        return (float) zDouble();
    }

    public static double xDouble() {
        return Math.sin(radians());
    }

    public static double zDouble() {
        return -Math.cos(radians());
    }

    public static double crossXDouble() {
        return -zDouble();
    }

    public static double crossZDouble() {
        return xDouble();
    }

    private static double radians() {
        return Math.toRadians(degrees());
    }
}
