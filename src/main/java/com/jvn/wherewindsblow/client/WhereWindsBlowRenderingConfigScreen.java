package com.jvn.wherewindsblow.client;

import com.jvn.wherewindsblow.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

public class WhereWindsBlowRenderingConfigScreen extends Screen {
    private final Screen lastScreen;

    public WhereWindsBlowRenderingConfigScreen(Screen lastScreen) {
        super(Component.translatable("where_winds_blow.config.rendering.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int left = center - 155;
        int right = center + 5;
        int y = 42;
        int rowSpacing = 20;

        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(left, y, ClientConfig.ENABLE_FOLIAGE_INTERACTIVITY, ClientConfig.SPEC));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.slider(right, y, 150, "foliageInteractivityStrength", ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(ClientConfig.FOLIAGE_INTERACTIVITY_STRENGTH, value), 0.0D, 2.0D));
        y += rowSpacing;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(left, y, ClientConfig.ENABLE_AUTODETECTED_FOLIAGE_MODELS, ClientConfig.SPEC));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(right, y, ClientConfig.ENABLE_CUSTOM_FOLIAGE_SHADER, ClientConfig.SPEC));
        y += rowSpacing;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(left, y, ClientConfig.ENABLE_WIND_SHEEN, ClientConfig.SPEC));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.slider(right, y, 150, "windSheenStrength", ClientConfig.WIND_SHEEN_STRENGTH::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(ClientConfig.WIND_SHEEN_STRENGTH, value), 0.0D, 2.0D));
        y += rowSpacing;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(left, y, ClientConfig.ENABLE_WIND_STREAKS, ClientConfig.SPEC));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.slider(right, y, 150, "windStreakVisibility", ClientConfig.WIND_STREAK_VISIBILITY::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(ClientConfig.WIND_STREAK_VISIBILITY, value), 0.0D, 2.0D));
        y += rowSpacing;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.slider(left, y, 150, "windStreakOpacity", ClientConfig.WIND_STREAK_OPACITY::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(ClientConfig.WIND_STREAK_OPACITY, value), 0.0D, 1.0D));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.slider(right, y, 150, "windStreakThickness", ClientConfig.WIND_STREAK_THICKNESS::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(ClientConfig.WIND_STREAK_THICKNESS, value), 1.0D, 4.0D));
        y += rowSpacing;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(left, y, ClientConfig.FORCE_WWB_WIND_WITH_SHADER_PACKS, ClientConfig.SPEC));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(right, y, ClientConfig.ENABLE_SODIUM_SHADER_PATCH, ClientConfig.SPEC));
        y += rowSpacing;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(left, y, ClientConfig.ENABLE_WIND_FOLIAGE_SWAY, ClientConfig.SPEC));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.slider(right, y, 150, "windFoliageSwayStrength", ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH::getAsDouble, value -> WhereWindsBlowConfigWidgets.set(ClientConfig.WIND_FOLIAGE_SWAY_STRENGTH, value), 0.0D, 2.0D));
        y += rowSpacing;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.slider(left, y, 150, "windPlantSwayStartHeight", ClientConfig.WIND_PLANT_SWAY_START_HEIGHT::getAsDouble, value -> setAndRebuildFoliage(ClientConfig.WIND_PLANT_SWAY_START_HEIGHT, value), 0.0D, 1.0D));
        y += rowSpacing;
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(left, y, ClientConfig.ENABLE_WIND_PLANT_SWAY, ClientConfig.SPEC));
        this.addRenderableWidget(WhereWindsBlowConfigWidgets.booleanButton(right, y, ClientConfig.ENABLE_WIND_LEAF_SWAY, ClientConfig.SPEC));

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
                .bounds(center - 100, this.height - 32, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("where_winds_blow.config.rendering.note"), this.width / 2, 32, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private static void setAndRebuildFoliage(ModConfigSpec.DoubleValue configValue, double value) {
        WhereWindsBlowConfigWidgets.set(configValue, value);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.levelRenderer.allChanged();
        }
    }
}
