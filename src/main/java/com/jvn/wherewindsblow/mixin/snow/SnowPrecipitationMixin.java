package com.jvn.wherewindsblow.mixin.snow;

import com.jvn.wherewindsblow.block.SnowLogging;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class SnowPrecipitationMixin {
    @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true, require = 1)
    private void accumulateInFoliage(BlockPos column, CallbackInfo ci) {
        if (SnowLogging.accumulate((ServerLevel) (Object) this, column)) {
            ci.cancel();
        }
    }
}
