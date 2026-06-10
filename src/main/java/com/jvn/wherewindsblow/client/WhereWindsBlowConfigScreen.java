package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.config.CommonConfig;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

public class WhereWindsBlowConfigScreen extends Screen {
    private static final double[] WORLDGEN_STEPS = {0.0D, 1.0D, 2.0D, 3.0D, 4.0D, 6.0D, 8.0D};

    private final Screen lastScreen;

    public WhereWindsBlowConfigScreen(Screen lastScreen) {
        super(Component.translatable("where_winds_blow.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = 44;

        this.addRenderableWidget(booleanButton(center - 155, y, CommonConfig.ENABLE_DENSE_GRASS_WORLDGEN, CommonConfig.SPEC));
        this.addRenderableWidget(doubleButton(center + 5, y, "where_winds_blow.config.denseGrassDensityMultiplier", CommonConfig.DENSE_GRASS_DENSITY_MULTIPLIER::getAsDouble, value -> set(CommonConfig.DENSE_GRASS_DENSITY_MULTIPLIER, value), WORLDGEN_STEPS));
        y += 24;
        this.addRenderableWidget(doubleButton(center - 155, y, "where_winds_blow.config.mediumGrassDensityMultiplier", CommonConfig.MEDIUM_GRASS_DENSITY_MULTIPLIER::getAsDouble, value -> set(CommonConfig.MEDIUM_GRASS_DENSITY_MULTIPLIER, value), WORLDGEN_STEPS));
        this.addRenderableWidget(doubleButton(center + 5, y, "where_winds_blow.config.sparseGrassDensityMultiplier", CommonConfig.SPARSE_GRASS_DENSITY_MULTIPLIER::getAsDouble, value -> set(CommonConfig.SPARSE_GRASS_DENSITY_MULTIPLIER, value), WORLDGEN_STEPS));
        y += 24;
        this.addRenderableWidget(doubleButton(center - 155, y, "where_winds_blow.config.tallGrassPatchMultiplier", CommonConfig.TALL_GRASS_PATCH_MULTIPLIER::getAsDouble, value -> set(CommonConfig.TALL_GRASS_PATCH_MULTIPLIER, value), WORLDGEN_STEPS));
        this.addRenderableWidget(doubleButton(center + 5, y, "where_winds_blow.config.overgrownGrassPatchMultiplier", CommonConfig.OVERGROWN_GRASS_PATCH_MULTIPLIER::getAsDouble, value -> set(CommonConfig.OVERGROWN_GRASS_PATCH_MULTIPLIER, value), WORLDGEN_STEPS));
        y += 24;
        this.addRenderableWidget(doubleButton(center - 155, y, "where_winds_blow.config.wildWheatPatchMultiplier", CommonConfig.WILD_WHEAT_PATCH_MULTIPLIER::getAsDouble, value -> set(CommonConfig.WILD_WHEAT_PATCH_MULTIPLIER, value), WORLDGEN_STEPS));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(center - 100, this.height - 32, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("where_winds_blow.config.worldgen.note"), this.width / 2, 28, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private static Button booleanButton(int x, int y, ModConfigSpec.BooleanValue value, ModConfigSpec spec) {
        String path = value.getPath().getLast();
        Supplier<Component> label = () -> optionLabel(path, value.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
        Button button = Button.builder(label.get(), clickedButton -> {
                    value.set(!value.getAsBoolean());
                    spec.save();
                    clickedButton.setMessage(label.get());
                })
                .bounds(x, y, 150, 20)
                .build();
        button.setTooltip(optionTooltip(path));
        return button;
    }

    private static Button doubleButton(int x, int y, String key, DoubleSupplier getter, Consumer<Double> setter, double[] values) {
        String path = key.substring(key.lastIndexOf('.') + 1);
        Supplier<Component> label = () -> optionLabel(path, Component.literal(format(getter.getAsDouble())));
        Button button = Button.builder(label.get(), clickedButton -> {
                    setter.accept(next(getter.getAsDouble(), values));
                    clickedButton.setMessage(label.get());
                })
                .bounds(x, y, 150, 20)
                .build();
        button.setTooltip(optionTooltip(path));
        return button;
    }

    private static void set(ModConfigSpec.DoubleValue value, double newValue) {
        value.set(newValue);
        value.save();
    }

    private static Component optionLabel(String path, Component value) {
        return Component.translatable("where_winds_blow.config." + path).append(": ").append(value);
    }

    private static Tooltip optionTooltip(String path) {
        return Tooltip.create(Component.translatable("where_winds_blow.config." + path + ".tooltip"));
    }

    private static double next(double current, double[] values) {
        for (double value : values) {
            if (value > current + 0.001D) {
                return value;
            }
        }

        return values[0];
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format("%.2f", value);
    }
}
