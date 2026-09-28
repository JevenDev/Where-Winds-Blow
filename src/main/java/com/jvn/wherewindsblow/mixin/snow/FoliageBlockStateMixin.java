package com.jvn.wherewindsblow.mixin.snow;

import com.jvn.wherewindsblow.block.SnowLogging;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class FoliageBlockStateMixin {
    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void snowOutline(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        BlockState state = (BlockState) (Object) this;
        if (SnowLogging.layers(state) > 0) {
            cir.setReturnValue(Shapes.or(cir.getReturnValue(), SnowLogging.snowState(state).getShape(level, pos, context)));
        }
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void snowCollision(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        BlockState state = (BlockState) (Object) this;
        if (SnowLogging.layers(state) > 0) {
            cir.setReturnValue(SnowLogging.snowState(state).getCollisionShape(level, pos, context));
        }
    }

    @Inject(method = "getBlockSupportShape", at = @At("HEAD"), cancellable = true, require = 1)
    private void snowSupport(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        BlockState state = (BlockState) (Object) this;
        if (SnowLogging.layers(state) > 0) {
            cir.setReturnValue(SnowLogging.snowState(state).getBlockSupportShape(level, pos));
        }
    }

    @Inject(method = "canBeReplaced(Lnet/minecraft/world/item/context/BlockPlaceContext;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void acceptSnowItem(BlockPlaceContext context, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = (BlockState) (Object) this;
        if (context.getItemInHand().is(Items.SNOW) && SnowLogging.canContainSnow(state)) {
            cir.setReturnValue(SnowLogging.layers(state) < 8);
        }
    }

    @Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true, require = 1)
    private void tickSnow(CallbackInfoReturnable<Boolean> cir) {
        if (SnowLogging.layers((BlockState) (Object) this) > 0) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "randomTick", at = @At("HEAD"), require = 1)
    private void meltSnow(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        SnowLogging.melt(level, pos, (BlockState) (Object) this);
    }
}
