package com.jvn.wherewindsblow.client.foliage;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class FoliageModelData {
    public static final ModelProperty<ColumnSegment> COLUMN_SEGMENT = new ModelProperty<>(ColumnSegment.class::isInstance);
    public static final ModelProperty<Boolean> WIND_EXPOSED = new ModelProperty<>(Boolean.class::isInstance);
    public static final ModelProperty<InteractionImpulse> INTERACTION_IMPULSE = new ModelProperty<>(InteractionImpulse.class::isInstance);

    private FoliageModelData() {
    }

    public record ColumnSegment(BlockPos rootPos, int offset, int height) {
        public ColumnSegment {
            rootPos = rootPos.immutable();
            offset = Math.max(0, offset);
            height = Math.max(1, height);
        }
    }

    public record InteractionImpulse(float offsetX, float offsetZ) {
        private static final float MAX_OFFSET = 0.62F;

        public InteractionImpulse {
            if (!Float.isFinite(offsetX)) {
                offsetX = 0.0F;
            }
            if (!Float.isFinite(offsetZ)) {
                offsetZ = 0.0F;
            }

            float length = (float) Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
            if (length > MAX_OFFSET) {
                float scale = MAX_OFFSET / length;
                offsetX *= scale;
                offsetZ *= scale;
            }
        }

        public boolean isVisible() {
            return offsetX * offsetX + offsetZ * offsetZ > 0.0001F;
        }
    }
}
