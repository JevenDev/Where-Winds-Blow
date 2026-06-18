package com.jvn.wherewindsblow.client.lantern;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class LanternSway {
    private LanternSway() {
    }

    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((location, model) -> shouldWrap(location, model) ? new LanternSwayModel(model) : model);
    }

    private static boolean shouldWrap(ModelResourceLocation location, BakedModel model) {
        return location.variant() != null
                && !location.variant().equals(ModelResourceLocation.INVENTORY_VARIANT)
                && !(model instanceof LanternSwayModel)
                && (location.id().equals(BuiltInRegistries.BLOCK.getKey(Blocks.LANTERN))
                || location.id().equals(BuiltInRegistries.BLOCK.getKey(Blocks.SOUL_LANTERN)));
    }
}
