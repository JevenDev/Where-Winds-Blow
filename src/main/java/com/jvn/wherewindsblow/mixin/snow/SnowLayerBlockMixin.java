package com.jvn.wherewindsblow.mixin.snow;

import com.jvn.wherewindsblow.block.SnowLogging;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SnowLayerBlock.class)
abstract class SnowLayerBlockMixin {
    @Inject(method = "getStateForPlacement", at = @At("HEAD"), cancellable = true, require = 1)
    private void snowlogFoliage(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (SnowLogging.canContainSnow(state)
                && SnowLogging.snowState(state).canSurvive(context.getLevel(), context.getClickedPos())) {
            cir.setReturnValue(SnowLogging.snowyVariant(state).setValue(SnowLogging.LAYERS, Math.min(8, SnowLogging.layers(state) + 1)));
        }
    }
}
