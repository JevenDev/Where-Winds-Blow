package com.jvn.wherewindsblow.client.wind;

public final class WindDirection {
    private static volatile WindVector cachedVector = new WindVector(Double.NaN, 0.0D, -1.0D);

    private WindDirection() {
    }

    public static WindVector current() {
        GlobalWindState state = DynamicWindManager.currentState();
        double degrees = state.directionDegrees();
        WindVector vector = cachedVector;
        if (Double.compare(vector.degrees(), degrees) == 0
                && Double.compare(vector.x(), state.directionX()) == 0
                && Double.compare(vector.z(), state.directionZ()) == 0) {
            return vector;
        }

        vector = new WindVector(degrees, state.directionX(), state.directionZ());
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
