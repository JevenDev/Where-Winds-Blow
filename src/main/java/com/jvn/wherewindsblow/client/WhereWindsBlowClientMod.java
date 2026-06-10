package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.WhereWindsBlow;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = WhereWindsBlow.MOD_ID, dist = Dist.CLIENT)
public final class WhereWindsBlowClientMod {
    public WhereWindsBlowClientMod(IEventBus modEventBus, ModContainer modContainer) {
        WhereWindsBlowClient.register(modEventBus, modContainer);
    }
}
