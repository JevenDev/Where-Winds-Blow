package com.jvn.wherewindsblow.block;

import com.jvn.wherewindsblow.WhereWindsBlow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockTags {
    public static final TagKey<Block> WILD_WHEAT_PLANTABLE_ON = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(WhereWindsBlow.MOD_ID, "wild_wheat_plantable_on")
    );

    private ModBlockTags() {
    }
}