package com.jvn.wherewindsblow.client.lantern;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class LanternModelData {
    public static final ModelProperty<ChainSegment> CHAIN_SEGMENT = new ModelProperty<>(ChainSegment.class::isInstance);
    public static final ModelProperty<Boolean> LANTERN_CHAIN_ATTACHED = new ModelProperty<>(Boolean.class::isInstance);
    public static final ModelProperty<Boolean> RENDERING_ASSEMBLY = new ModelProperty<>(Boolean.class::isInstance);
    public static final ModelProperty<Boolean> WIND_EXPOSED = new ModelProperty<>(Boolean.class::isInstance);

    private LanternModelData() {
    }

    public record ChainSegment(BlockPos topPos, int offsetFromTop, int height, boolean hangingLanternAttached, boolean windExposed) {
        public ChainSegment {
            topPos = topPos.immutable();
            offsetFromTop = Math.max(0, offsetFromTop);
            height = Math.max(1, height);
        }
    }
}
