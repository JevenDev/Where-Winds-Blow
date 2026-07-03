package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.smoke.CampfireSmokePlumes;
import com.jvn.wherewindsblow.client.smoke.CampfireSmokePlumes.SmokeCluster;
import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {
    @Inject(method = "particleTick", at = @At("HEAD"), cancellable = true)
    private static void wherewindsblow$replaceCampfireSmoke(
            Level level,
            BlockPos pos,
            BlockState state,
            CampfireBlockEntity blockEntity,
            CallbackInfo ci
    ) {
        if (!ClientConfig.ENABLE_WIND_SMOKE.getAsBoolean()
                || ClientConfig.WIND_SMOKE_STRENGTH.getAsDouble() <= 0.0D
                || !CampfireSmokePlumes.canReplaceSmoke(level, pos, state)) {
            return;
        }

        SmokeCluster cluster = CampfireSmokePlumes.findCluster(level, pos);
        if (cluster == null) {
            return;
        }

        ci.cancel();
        if (!pos.equals(cluster.leader())) {
            return;
        }

        CampfireSmokePlumes.spawnMergedSmoke(level, cluster);
        CampfireSmokePlumes.spawnCookingSmoke(level, cluster);
    }
}
