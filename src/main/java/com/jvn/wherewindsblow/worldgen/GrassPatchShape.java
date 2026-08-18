package com.jvn.wherewindsblow.worldgen;

import net.minecraft.util.RandomSource;

final class GrassPatchShape {
    private static final int NOISE_SCALE = 4;
    private static final double EDGE_WARP = 0.13D;

    private final double radiusX;
    private final double radiusZ;
    private final double cosine;
    private final double sine;
    private final long seed;

    private GrassPatchShape(double radiusX, double radiusZ, double angle, long seed) {
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.cosine = Math.cos(angle);
        this.sine = Math.sin(angle);
        this.seed = seed;
    }

    static GrassPatchShape create(
            RandomSource random,
            double minRadiusX,
            double maxRadiusX,
            double minRadiusZ,
            double maxRadiusZ
    ) {
        double radiusX = minRadiusX + random.nextDouble() * (maxRadiusX - minRadiusX);
        double radiusZ = minRadiusZ + random.nextDouble() * (maxRadiusZ - minRadiusZ);
        return new GrassPatchShape(radiusX, radiusZ, random.nextDouble() * Math.PI, random.nextLong());
    }

    int bounds() {
        return (int) Math.ceil(Math.max(radiusX, radiusZ) + 1.0D);
    }

    double distance(int offsetX, int offsetZ, int worldX, int worldZ) {
        double rotatedX = offsetX * cosine - offsetZ * sine;
        double rotatedZ = offsetX * sine + offsetZ * cosine;
        double ovalDistance = Math.sqrt(
                rotatedX * rotatedX / (radiusX * radiusX)
                        + rotatedZ * rotatedZ / (radiusZ * radiusZ)
        );
        return Math.max(0.0D, ovalDistance + valueNoise(worldX, worldZ, seed) * EDGE_WARP);
    }

    double coherence(int worldX, int worldZ) {
        return valueNoise(worldX, worldZ, seed ^ 0x6A09E667F3BCC909L);
    }

    static double valueNoise(int x, int z, long seed) {
        int cellX = Math.floorDiv(x, NOISE_SCALE);
        int cellZ = Math.floorDiv(z, NOISE_SCALE);
        double fractionX = Math.floorMod(x, NOISE_SCALE) / (double) NOISE_SCALE;
        double fractionZ = Math.floorMod(z, NOISE_SCALE) / (double) NOISE_SCALE;
        double smoothX = smooth(fractionX);
        double smoothZ = smooth(fractionZ);

        double top = lerp(lattice(cellX, cellZ, seed), lattice(cellX + 1, cellZ, seed), smoothX);
        double bottom = lerp(lattice(cellX, cellZ + 1, seed), lattice(cellX + 1, cellZ + 1, seed), smoothX);
        return lerp(top, bottom, smoothZ);
    }

    private static double lattice(int x, int z, long seed) {
        long value = seed;
        value ^= x * 0x9E3779B97F4A7C15L;
        value ^= z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 11) * 0x1.0p-53) * 2.0D - 1.0D;
    }

    private static double smooth(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private static double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }
}
