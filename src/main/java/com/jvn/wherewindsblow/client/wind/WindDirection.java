package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.config.ClientConfig;

public final class WindDirection {
    private static volatile WindVector cachedVector = new WindVector(Double.NaN, 0.0D, -1.0D);

    private WindDirection() {
    }

    private static double degrees() {
        return ClientConfig.WIND_DIRECTION_DEGREES.getAsDouble();
    }

    public static WindVector current() {
        double degrees = degrees();
        WindVector vector = cachedVector;
        if (Double.compare(vector.degrees(), degrees) == 0) {
            return vector;
        }

        double radians = Math.toRadians(degrees);
        vector = new WindVector(degrees, Math.sin(radians), -Math.cos(radians));
        cachedVector = vector;
        return vector;
    }

    public record WindVector(double degrees, double x, double z) {
        public float xFloat() {
            return (float) x;
        }

        public float zFloat() {
            return (float) z;
        }

        public double crossX() {
            return -z;
        }

        public double crossZ() {
            return x;
        }
    }
}
