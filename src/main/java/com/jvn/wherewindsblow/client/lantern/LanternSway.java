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
        event.getModels().replaceAll((location, model) -> {
            LanternSwayModel.Subject subject = subjectFor(location, model);
            return subject != null
                ? new LanternSwayModel(model, subject)
                : model;
        });
    }

    private static LanternSwayModel.Subject subjectFor(ModelResourceLocation location, BakedModel model) {
        if (location.variant() == null
                || location.variant().equals(ModelResourceLocation.INVENTORY_VARIANT)
                || model instanceof LanternSwayModel) {
            return null;
        }

        if (location.id().equals(BuiltInRegistries.BLOCK.getKey(Blocks.LANTERN))
                || location.id().equals(BuiltInRegistries.BLOCK.getKey(Blocks.SOUL_LANTERN))) {
            return LanternSwayModel.Subject.LANTERN;
        }

        return location.id().equals(BuiltInRegistries.BLOCK.getKey(Blocks.CHAIN))
                ? LanternSwayModel.Subject.CHAIN
                : null;
    }
}
