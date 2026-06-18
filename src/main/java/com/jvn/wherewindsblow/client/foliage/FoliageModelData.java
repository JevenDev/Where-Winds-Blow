package com.jvn.wherewindsblow.client.foliage;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class FoliageModelData {
    public static final ModelProperty<ColumnSegment> COLUMN_SEGMENT = new ModelProperty<>(ColumnSegment.class::isInstance);
    public static final ModelProperty<Boolean> WIND_EXPOSED = new ModelProperty<>(Boolean.class::isInstance);

    private FoliageModelData() {
    }

    public record ColumnSegment(BlockPos rootPos, int offset, int height) {
        public ColumnSegment {
            rootPos = rootPos.immutable();
            offset = Math.max(0, offset);
            height = Math.max(1, height);
        }
    }
}
