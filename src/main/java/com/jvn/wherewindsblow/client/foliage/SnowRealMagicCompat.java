package com.jvn.wherewindsblow.client.foliage;

import com.jvn.wherewindsblow.WhereWindsBlow;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class SnowRealMagicCompat {
    private static final String MOD_NAMESPACE = "snowrealmagic";
    private static final String MODEL_PACKAGE = "snownee.snow.client.model.";
    private static final String BLOCK_ENTITY_PACKAGE = "snownee.snow.block.entity.";
    private static final ThreadLocal<Deque<RenderContext>> ACTIVE_RENDERS = new ThreadLocal<>();
    private static boolean containedStateWarningLogged;

    private static final ClassValue<Optional<Method>> CONTAINED_STATE_ACCESSORS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            if (!type.getName().startsWith(BLOCK_ENTITY_PACKAGE)) {
                return Optional.empty();
            }
            try {
                Method method = type.getMethod("getContainedState");
                return method.getReturnType() == BlockState.class ? Optional.of(method) : Optional.empty();
            } catch (NoSuchMethodException exception) {
                return Optional.empty();
            }
        }
    };

    private SnowRealMagicCompat() {
    }

    public static void beginRender(BlockAndTintGetter level, BlockPos pos) {
        Deque<RenderContext> renders = ACTIVE_RENDERS.get();
        if (renders == null) {
            renders = new ArrayDeque<>();
            ACTIVE_RENDERS.set(renders);
        }
        renders.push(new RenderContext(level, pos.immutable()));
    }

    public static void endRender() {
        Deque<RenderContext> renders = ACTIVE_RENDERS.get();
        if (renders == null) {
            return;
        }
        if (!renders.isEmpty()) {
            renders.pop();
        }
        if (renders.isEmpty()) {
            ACTIVE_RENDERS.remove();
        }
    }

    @Nullable
    static RenderContext currentRender() {
        Deque<RenderContext> renders = ACTIVE_RENDERS.get();
        return renders != null ? renders.peek() : null;
    }

    static boolean isModel(Object model) {
        return model.getClass().getName().startsWith(MODEL_PACKAGE);
    }

    static BlockState effectiveState(BlockAndTintGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!MOD_NAMESPACE.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
            return state;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return state;
        }
        Optional<Method> accessor = CONTAINED_STATE_ACCESSORS.get(blockEntity.getClass());
        if (accessor.isEmpty()) {
            return state;
        }

        try {
            Object contained = accessor.get().invoke(blockEntity);
            return contained instanceof BlockState containedState ? containedState : state;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            if (!containedStateWarningLogged) {
                containedStateWarningLogged = true;
                WhereWindsBlow.LOGGER.warn(
                        "Could not read Snow Real Magic's contained block state; snow-covered foliage compatibility is limited.",
                        exception
                );
            }
            return state;
        }
    }

    record RenderContext(BlockAndTintGetter level, BlockPos pos) {
    }
}
