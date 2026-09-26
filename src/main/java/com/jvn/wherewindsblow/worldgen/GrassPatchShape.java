package com.jvn.wherewindsblow.worldgen;

import net.minecraft.util.RandomSource;

final class GrassPatchShape {
    private static final int NOISE_SCALE = 4;
    private static final double EDGE_WARP = 0.08D;
    private static final double PATCH_LIMIT = 1.18D;

    private final double radiusX;
    private final double radiusZ;
    private final double cosine;
    private final double sine;
    private final long seed;
    private final Lobe[] lobes;

    private GrassPatchShape(double radiusX, double radiusZ, double angle, long seed, Lobe[] lobes) {
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.cosine = Math.cos(angle);
        this.sine = Math.sin(angle);
        this.seed = seed;
        this.lobes = lobes;
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
        Lobe[] lobes = new Lobe[random.nextInt(2, 5)];
        lobes[0] = new Lobe(0, 0, 0.15D + random.nextDouble() * 0.20D);
        for (int index = 1; index < lobes.length; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double reach = 1.5D + random.nextDouble() * 1.5D;
            int centerX = (int) Math.round(Math.cos(angle) * reach);
            int centerZ = (int) Math.round(Math.sin(angle) * reach);
            double heightOffset = index == 1 ? 0.0D : random.nextDouble() * 0.45D;
            lobes[index] = new Lobe(centerX, centerZ, heightOffset);
        }
        return new GrassPatchShape(radiusX, radiusZ, random.nextDouble() * Math.PI, random.nextLong(), lobes);
    }

    int bounds() {
        int centerExtent = 0;
        for (Lobe lobe : lobes) {
            centerExtent = Math.max(centerExtent, Math.max(Math.abs(lobe.x()), Math.abs(lobe.z())));
        }
        return centerExtent + (int) Math.ceil(Math.max(radiusX, radiusZ) * (PATCH_LIMIT + EDGE_WARP));
    }

    double distance(int offsetX, int offsetZ, int worldX, int worldZ) {
        double distance = Double.POSITIVE_INFINITY;
        for (Lobe lobe : lobes) {
            double x = offsetX - lobe.x();
            double z = offsetZ - lobe.z();
            double rotatedX = x * cosine - z * sine;
            double rotatedZ = x * sine + z * cosine;
            double ovalDistance = Math.sqrt(
                    rotatedX * rotatedX / (radiusX * radiusX)
                            + rotatedZ * rotatedZ / (radiusZ * radiusZ)
            );
            distance = Math.min(distance, ovalDistance + lobe.heightOffset());
        }
        return Math.max(0.0D, distance + valueNoise(worldX, worldZ, seed, 6) * EDGE_WARP);
    }

    int grassHeight(int offsetX, int offsetZ, int worldX, int worldZ, int peakHeight) {
        double distance = distance(offsetX, offsetZ, worldX, worldZ);
        if (distance > PATCH_LIMIT) {
            return 0;
        }

        double edgeHeight = peakHeight + 0.25D - distance * (peakHeight - 1) / 0.9D;
        int heightNoiseScale = peakHeight * 2;
        double variation = valueNoise(worldX, worldZ, seed ^ 0x6A09E667F3BCC909L, heightNoiseScale);
        double localHeight = (peakHeight + 1) * 0.5D + variation * peakHeight * 0.65D;
        return Math.clamp(Math.round(Math.min(edgeHeight, localHeight)), 0, peakHeight);
    }

    static double valueNoise(int x, int z, long seed) {
        return valueNoise(x, z, seed, NOISE_SCALE);
    }

    private static double valueNoise(int x, int z, long seed, int scale) {
        int cellX = Math.floorDiv(x, scale);
        int cellZ = Math.floorDiv(z, scale);
        double fractionX = Math.floorMod(x, scale) / (double) scale;
        double fractionZ = Math.floorMod(z, scale) / (double) scale;
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

    private record Lobe(int x, int z, double heightOffset) {
    }
}
