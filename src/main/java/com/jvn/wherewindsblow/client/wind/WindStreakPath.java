package com.jvn.wherewindsblow.client.wind;

import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;

import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.Point;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.isWindLineSpace;
import static com.jvn.wherewindsblow.client.wind.WindParticleEnvironment.lerp;

final class WindStreakPath {
    private final Point[] previous;
    private final Point[] current;
    private final Point[] candidate;
    private boolean initialized;

    WindStreakPath(int segments) {
        previous = new Point[segments + 1];
        current = new Point[segments + 1];
        candidate = new Point[segments + 1];
    }

    void reset() {
        initialized = false;
    }

    void setCandidate(int index, Point point) {
        candidate[index] = point;
    }

    void hold() {
        System.arraycopy(current, 0, previous, 0, current.length);
    }

    boolean advance(LevelReader level, double clearance) {
        for (int index = 1; index < candidate.length; index++) {
            AABB bounds = bounds(candidate[index - 1], candidate[index]);
            if (initialized) {
                // include the swept area so interpolation cannot cut through a roof or hillside
                bounds = bounds.minmax(bounds(current[index - 1], current[index]));
            }
            if (!isWindLineSpace(level, bounds, clearance)) {
                return false;
            }
        }
        System.arraycopy(initialized ? current : candidate, 0, previous, 0, current.length);
        System.arraycopy(candidate, 0, current, 0, current.length);
        initialized = true;
        return true;
    }

    boolean isValid(LevelReader level, double clearance) {
        if (!initialized) {
            return false;
        }
        for (int index = 1; index < current.length; index++) {
            if (!isWindLineSpace(level, bounds(current[index - 1], current[index]), clearance)) {
                return false;
            }
        }
        return true;
    }

    Point interpolate(int index, float partialTick) {
        Point from = previous[index];
        Point to = current[index];
        return new Point(lerp(partialTick, from.x(), to.x()),
                lerp(partialTick, from.y(), to.y()), lerp(partialTick, from.z(), to.z()));
    }

    private static AABB bounds(Point from, Point to) {
        return new AABB(Math.min(from.x(), to.x()), Math.min(from.y(), to.y()), Math.min(from.z(), to.z()),
                Math.max(from.x(), to.x()), Math.max(from.y(), to.y()), Math.max(from.z(), to.z()));
    }
}
