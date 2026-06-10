package com.jvn.wherewindsblow.block;

import com.jvn.wherewindsblow.WhereWindsBlow;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TallGrassBlock;
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

    public static final DeferredBlock<TallGrassBlock> FLAT_GRASS = BLOCKS.registerBlock(
            "flat_grass",
            TallGrassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
    );

    public static final DeferredBlock<DryGrassBlock> FLAT_DEAD_GRASS = BLOCKS.registerBlock(
            "flat_dead_grass",
            DryGrassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
    );

    public static final DeferredBlock<DryGrassBlock> SHORT_DEAD_GRASS = BLOCKS.registerBlock(
            "short_dead_grass",
            DryGrassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
    );

    public static final DeferredBlock<DryGrassBlock> SHORT_DRY_GRASS = BLOCKS.registerBlock(
            "short_dry_grass",
            DryGrassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
    );

    public static final DeferredBlock<DryGrassBlock> TALL_DRY_GRASS = BLOCKS.registerBlock(
            "tall_dry_grass",
            DryGrassBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
    );

    public static final DeferredBlock<WildWheatBlock> WILD_WHEAT = BLOCKS.registerBlock(
            "wild_wheat",
            WildWheatBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
    );

    public static final DeferredItem<BlockItem> OVERGROWN_GRASS_ITEM = ITEMS.registerSimpleBlockItem(OVERGROWN_GRASS, new Item.Properties());
    public static final DeferredItem<BlockItem> FLAT_GRASS_ITEM = ITEMS.registerSimpleBlockItem(FLAT_GRASS, new Item.Properties());
    public static final DeferredItem<BlockItem> FLAT_DEAD_GRASS_ITEM = ITEMS.registerSimpleBlockItem(FLAT_DEAD_GRASS, new Item.Properties());
    public static final DeferredItem<BlockItem> SHORT_DEAD_GRASS_ITEM = ITEMS.registerSimpleBlockItem(SHORT_DEAD_GRASS, new Item.Properties());
    public static final DeferredItem<BlockItem> SHORT_DRY_GRASS_ITEM = ITEMS.registerSimpleBlockItem(SHORT_DRY_GRASS, new Item.Properties());
    public static final DeferredItem<BlockItem> TALL_DRY_GRASS_ITEM = ITEMS.registerSimpleBlockItem(TALL_DRY_GRASS, new Item.Properties());
    public static final DeferredItem<BlockItem> WILD_WHEAT_ITEM = ITEMS.registerSimpleBlockItem(WILD_WHEAT, new Item.Properties());

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
            event.accept(FLAT_GRASS_ITEM);
            event.accept(FLAT_DEAD_GRASS_ITEM);
            event.accept(SHORT_DEAD_GRASS_ITEM);
            event.accept(SHORT_DRY_GRASS_ITEM);
            event.accept(TALL_DRY_GRASS_ITEM);
            event.accept(WILD_WHEAT_ITEM);
        }
    }
}
