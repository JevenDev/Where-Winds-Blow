package com.jvn.wherewindsblow.mixin.client.compat.snowrealmagic;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliage;
import com.jvn.wherewindsblow.client.foliage.SnowRealMagicCompat;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "snownee.snow.client.model.SnowVariantModel", remap = false)
public abstract class SnowRealMagicVariantModelMixin {
    @Shadow
    @Final
    @Mutable
    private BakedModel variantModel;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void wherewindsblow$wrapSnowVariantModel(
            BakedModel model,
            BakedModel variantModel,
            CallbackInfo ci
    ) {
        this.variantModel = ResponsiveFoliage.wrapSnowRealMagicVariant(this.variantModel);
    }

    @Inject(method = "emitBlockQuads", at = @At("HEAD"))
    private void wherewindsblow$beginSnowVariantRender(
            CallbackInfo ci,
            @Local(argsOnly = true) BlockAndTintGetter level,
            @Local(argsOnly = true) BlockPos pos
    ) {
        if (pos != null) {
            SnowRealMagicCompat.beginRender(level, pos);
        }
    }

    @Inject(method = "emitBlockQuads", at = @At("RETURN"))
    private void wherewindsblow$endSnowVariantRender(
            CallbackInfo ci,
            @Local(argsOnly = true) BlockPos pos
    ) {
        if (pos != null) {
            SnowRealMagicCompat.endRender();
        }
    }
}
