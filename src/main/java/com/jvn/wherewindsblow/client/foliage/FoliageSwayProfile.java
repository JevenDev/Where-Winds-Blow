package com.jvn.wherewindsblow.client.foliage;

import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

record FoliageSwayProfile(
        ResourceLocation id,
        Set<ResourceLocation> blocks,
        List<TagKey<Block>> blockTags,
        ResponsiveFoliageType type,
        int priority,
        boolean interactive,
        float swayStartHeightMultiplier,
        float heightScale,
        float interactionHeightScaleMultiplier,
        float swayStrengthMultiplier,
        float interactionStrengthMultiplier
) {
    boolean matches(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blocks.contains(blockId)) {
            return true;
        }
        for (TagKey<Block> tag : blockTags) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
