package com.jvn.wherewindsblow.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class WhereWindsBlowMixinPlugin implements IMixinConfigPlugin {
    private static final String ACEDIUM_MOD_ID = "acedium";
    private static final String CHUNKS_FADE_IN_MOD_ID = "chunksfadein";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.") && isModLoaded(CHUNKS_FADE_IN_MOD_ID)) {
            return false;
        }

        if (mixinClassName.contains(".compat.acedium.")) {
            return isModLoaded(ACEDIUM_MOD_ID) && classExists(targetClassName);
        }

        if (mixinClassName.contains(".compat.sodium.")) {
            return isModLoaded("sodium") && classExists(targetClassName);
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isModLoaded(String modId) {
        try {
            LoadingModList loadingModList = LoadingModList.get();
            return loadingModList != null && loadingModList.getModFileById(modId) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean classExists(String className) {
        String resourceName = className.replace('.', '/') + ".class";
        return hasClassResource(WhereWindsBlowMixinPlugin.class.getClassLoader(), resourceName)
                || hasClassResource(Thread.currentThread().getContextClassLoader(), resourceName);
    }

    private static boolean hasClassResource(ClassLoader classLoader, String resourceName) {
        return classLoader != null && classLoader.getResource(resourceName) != null;
    }
}
