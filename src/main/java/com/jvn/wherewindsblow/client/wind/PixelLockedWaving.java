package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.config.ClientConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PixelLockedWaving {
    private PixelLockedWaving() {
    }

    public static boolean enabled() {
        return ClientConfig.PIXEL_LOCKED_WAVING.getAsBoolean();
    }

    public static double snap(double value) {
        return enabled() ? Math.floor(value * 16.0D + 0.5D) / 16.0D : value;
    }

    public static float snap(float value) {
        return (float) snap((double) value);
    }

    public static float angle(float radians, float radius) {
        return enabled() && radius > 0.0F ? (float) (snap((double) radians * radius) / radius) : radians;
    }

    public static MultiBufferSource buffers(MultiBufferSource source, Matrix4f restingPose, Matrix4f animatedPose) {
        if (!enabled()) {
            return source;
        }
        // snap displacement from the resting model so thin geometry keeps its original dimensions
        Matrix4f toRest = new Matrix4f(restingPose).mul(new Matrix4f(animatedPose).invert());
        return type -> new LockedConsumer(source.getBuffer(type), toRest, 0.0F, 0.0F, 0.0F);
    }

    public static VertexConsumer particleConsumer(VertexConsumer consumer, Vec3 position) {
        if (!enabled()) {
            return consumer;
        }
        return new LockedConsumer(consumer, null,
                (float) (snap(position.x) - position.x),
                (float) (snap(position.y) - position.y),
                (float) (snap(position.z) - position.z));
    }

    private static final class LockedConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final Matrix4f toRest;
        private final Vector3f restingPosition = new Vector3f();
        private final float shiftX;
        private final float shiftY;
        private final float shiftZ;

        private LockedConsumer(VertexConsumer delegate, Matrix4f toRest, float shiftX, float shiftY, float shiftZ) {
            this.delegate = delegate;
            this.toRest = toRest;
            this.shiftX = shiftX;
            this.shiftY = shiftY;
            this.shiftZ = shiftZ;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (this.toRest != null) {
                this.toRest.transformPosition(x, y, z, this.restingPosition);
                x = this.restingPosition.x + snap(x - this.restingPosition.x);
                y = this.restingPosition.y + snap(y - this.restingPosition.y);
                z = this.restingPosition.z + snap(z - this.restingPosition.z);
            }
            this.delegate.addVertex(x + this.shiftX, y + this.shiftY, z + this.shiftZ);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }
}
