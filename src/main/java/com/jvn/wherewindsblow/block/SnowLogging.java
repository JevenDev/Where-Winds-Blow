package com.jvn.wherewindsblow.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SnowLogging {
    public static final IntegerProperty LAYERS = IntegerProperty.create("snow_layers", 0, 8);

    private static final ThreadLocal<Boolean> RESTORING = ThreadLocal.withInitial(() -> false);

    private SnowLogging() {
    }

    public static boolean supports(Block block) {
        return block instanceof BushBlock && !(block instanceof CropBlock) && !(block instanceof SaplingBlock)
                && !(block instanceof StemBlock) && !(block instanceof AttachedStemBlock)
                && !(block instanceof SeagrassBlock) && !(block instanceof TallSeagrassBlock)
                && !(block instanceof WaterlilyBlock) && !(block instanceof NetherWartBlock)
                && !(block instanceof PitcherCropBlock);
    }

    public static int layers(BlockState state) {
        return state.hasProperty(LAYERS) ? state.getValue(LAYERS) : 0;
    }

    public static boolean canContainSnow(BlockState state) {
        return state.hasProperty(LAYERS)
                && (!state.hasProperty(BlockStateProperties.WATERLOGGED) || !state.getValue(BlockStateProperties.WATERLOGGED))
                && (!state.hasProperty(DoublePlantBlock.HALF) || state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER);
    }

    public static BlockState snowState(BlockState state) {
        return Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.max(1, layers(state)));
    }

    public static BlockState mergePlacement(LevelAccessor level, BlockPos pos, BlockState next) {
        if (RESTORING.get()) {
            return next;
        }
        BlockState previous = level.getBlockState(pos);
        if (next.is(Blocks.SNOW) && canContainSnow(previous) && next.canSurvive(level, pos)) {
            return previous.setValue(LAYERS, Math.max(layers(previous), next.getValue(SnowLayerBlock.LAYERS)));
        }
        if (canContainSnow(next) && !next.is(previous.getBlock())) {
            int layers = previous.is(Blocks.SNOW) ? previous.getValue(SnowLayerBlock.LAYERS) : layers(previous);
            if (layers > 0 && snowState(previous).canSurvive(level, pos)) {
                return next.setValue(LAYERS, layers);
            }
        }
        if (next.isAir() && layers(previous) > 0 && snowState(previous).canSurvive(level, pos)) {
            return snowState(previous);
        }
        return next;
    }

    public static void restorePlacement(LevelAccessor level, BlockPos pos, BlockState state, int flags) {
        boolean previous = RESTORING.get();
        RESTORING.set(true);
        try {
            level.setBlock(pos, state, flags);
        } finally {
            RESTORING.set(previous);
        }
    }

    public static boolean shouldSnow(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return canContainSnow(state) && layers(state) < 8 && !level.isOutsideBuildHeight(pos)
                && level.getBrightness(LightLayer.BLOCK, pos) < 10
                && snowState(state).canSurvive(level, pos);
    }

    public static boolean accumulate(ServerLevel level, BlockPos column) {
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        if (!canContainSnow(level.getBlockState(pos))) {
            pos = pos.below();
        }
        BlockState state = level.getBlockState(pos);
        if (!canContainSnow(state) || !level.isRaining() || !level.isAreaLoaded(pos, 1)
                || level.getBiome(pos).value().warmEnoughToRain(pos)) {
            return false;
        }
        int maximum = Math.min(8, level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT));
        if (layers(state) < maximum && shouldSnow(level, pos)) {
            BlockState snowier = state.setValue(LAYERS, layers(state) + 1);
            Block.pushEntitiesUp(state, snowier, level, pos);
            level.setBlockAndUpdate(pos, snowier);
        }
        return true;
    }

    public static void melt(ServerLevel level, BlockPos pos, BlockState state) {
        if (layers(state) > 0 && level.getBrightness(LightLayer.BLOCK, pos) > 11) {
            level.setBlockAndUpdate(pos, state.setValue(LAYERS, 0));
        }
    }

    public static void onShovel(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        int layers = layers(state);
        if (layers == 0 || !event.getItemStack().is(ItemTags.SHOVELS)
                || !event.getEntity().mayUseItemAt(pos, event.getFace(), event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!level.isClientSide && level.setBlockAndUpdate(pos, state.setValue(LAYERS, 0))) {
            level.levelEvent(2001, pos, Block.getId(snowState(state)));
            if (!event.getEntity().isCreative()) {
                Block.popResource(level, pos, new ItemStack(Items.SNOWBALL, layers));
                event.getItemStack().hurtAndBreak(1, event.getEntity(),
                        event.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
    }
}
