package com.jvn.wherewindsblow.block;

import com.jvn.wherewindsblow.WhereWindsBlow;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WhereWindsBlow.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WhereWindsBlow.MOD_ID);

    public static final DeferredBlock<OvergrownGrassBlock> OVERGROWN_GRASS = BLOCKS.registerBlock(
            "overgrown_grass",
            OvergrownGrassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.TALL_GRASS)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
    );

    public static final DeferredItem<BlockItem> OVERGROWN_GRASS_ITEM = ITEMS.registerSimpleBlockItem(OVERGROWN_GRASS, new Item.Properties());

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModBlocks::addCreativeTabContents);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(OVERGROWN_GRASS_ITEM);
        }
    }
}