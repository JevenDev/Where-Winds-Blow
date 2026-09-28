package com.jvn.wherewindsblow.mixin.snow;

import com.jvn.wherewindsblow.block.SnowLogging;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Level.class, WorldGenRegion.class})
abstract class SnowPlacementMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void preserveFoliageAndSnow(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Level level && level.isClientSide) {
            return;
        }
        Boolean placed = SnowLogging.placeMerged((LevelAccessor) this, pos, state, flags, recursionLeft);
        if (placed != null) {
            cir.setReturnValue(placed);
        }
    }
}
