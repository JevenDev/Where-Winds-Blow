package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.config.CommonConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class WhereWindsBlowTerrainConfigScreen extends Screen {
    private static final double[] WORLDGEN_STEPS = {0.0D, 1.0D, 2.0D, 3.0D, 4.0D, 6.0D, 8.0D};

    private final Screen lastScreen;

    public WhereWindsBlowTerrainConfigScreen(Screen lastScreen) {
        super(Component.translatable("where_winds_blow.config.terrain.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = 44;

        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(center - 155, y, CommonConfig.ENABLE_DENSE_GRASS_WORLDGEN, CommonConfig.SPEC));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.steppedDoubleButton(center + 5, y, "where_winds_blow.config.denseGrassDensityMultiplier", CommonConfig.DENSE_GRASS_DENSITY_MULTIPLIER::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(CommonConfig.DENSE_GRASS_DENSITY_MULTIPLIER, value), WORLDGEN_STEPS));
        y += 24;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.steppedDoubleButton(center - 155, y, "where_winds_blow.config.mediumGrassDensityMultiplier", CommonConfig.MEDIUM_GRASS_DENSITY_MULTIPLIER::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(CommonConfig.MEDIUM_GRASS_DENSITY_MULTIPLIER, value), WORLDGEN_STEPS));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.steppedDoubleButton(center + 5, y, "where_winds_blow.config.sparseGrassDensityMultiplier", CommonConfig.SPARSE_GRASS_DENSITY_MULTIPLIER::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(CommonConfig.SPARSE_GRASS_DENSITY_MULTIPLIER, value), WORLDGEN_STEPS));
        y += 24;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.steppedDoubleButton(center - 155, y, "where_winds_blow.config.tallGrassPatchMultiplier", CommonConfig.TALL_GRASS_PATCH_MULTIPLIER::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(CommonConfig.TALL_GRASS_PATCH_MULTIPLIER, value), WORLDGEN_STEPS));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.steppedDoubleButton(center + 5, y, "where_winds_blow.config.shortDryGrassPatchMultiplier", CommonConfig.SHORT_DRY_GRASS_PATCH_MULTIPLIER::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(CommonConfig.SHORT_DRY_GRASS_PATCH_MULTIPLIER, value), WORLDGEN_STEPS));
        y += 24;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.steppedDoubleButton(center - 155, y, "where_winds_blow.config.tallDryGrassPatchMultiplier", CommonConfig.TALL_DRY_GRASS_PATCH_MULTIPLIER::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(CommonConfig.TALL_DRY_GRASS_PATCH_MULTIPLIER, value), WORLDGEN_STEPS));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.steppedDoubleButton(center + 5, y, "where_winds_blow.config.overgrownGrassPatchMultiplier", CommonConfig.OVERGROWN_GRASS_PATCH_MULTIPLIER::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(CommonConfig.OVERGROWN_GRASS_PATCH_MULTIPLIER, value), WORLDGEN_STEPS));
        y += 24;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.steppedDoubleButton(center - 155, y, "where_winds_blow.config.deadGrassPatchMultiplier", CommonConfig.DEAD_GRASS_PATCH_MULTIPLIER::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(CommonConfig.DEAD_GRASS_PATCH_MULTIPLIER, value), WORLDGEN_STEPS));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.steppedDoubleButton(center + 5, y, "where_winds_blow.config.wildWheatPatchMultiplier", CommonConfig.WILD_WHEAT_PATCH_MULTIPLIER::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(CommonConfig.WILD_WHEAT_PATCH_MULTIPLIER, value), WORLDGEN_STEPS));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
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
}
