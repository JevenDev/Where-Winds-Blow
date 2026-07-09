package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.smoke.CampfireSmokeRenderTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Redirect(
            method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Queue;iterator()Ljava/util/Iterator;")
    )
    private Iterator<Particle> wherewindsblow$sortMergedPlumeBackToFront(
            Queue<Particle> particles,
            LightTexture lightTexture,
            Camera camera,
            float partialTick,
            Frustum frustum,
            Predicate<ParticleRenderType> renderTypePredicate
    ) {
        Particle first = particles.peek();
        if (first == null || first.getRenderType() != CampfireSmokeRenderTypes.MERGED_PLUME) {
            return particles.iterator();
        }

        List<Particle> sorted = new ArrayList<>(particles);
        sorted.sort(Comparator.comparingDouble(
                (Particle particle) -> particle.getPos().distanceToSqr(camera.getPosition())
        ).reversed());
        return sorted.iterator();
    }
}
