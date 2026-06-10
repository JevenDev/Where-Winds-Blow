package com.jvn.wherewindsblow.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class WhereWindsBlowConfigScreen extends Screen {
    private final Screen lastScreen;

    public WhereWindsBlowConfigScreen(Screen lastScreen) {
        super(Component.translatable("where_winds_blow.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = this.height / 2 - 28;

        this.addRenderableWidget(Button.builder(Component.translatable("where_winds_blow.config.terrain"), button -> this.minecraft.setScreen(new WhereWindsBlowTerrainConfigScreen(this)))
                .bounds(center - 100, y, 200, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("where_winds_blow.config.rendering"), button -> this.minecraft.setScreen(new WhereWindsBlowRenderingConfigScreen(this)))
                .bounds(center - 100, y + 24, 200, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(center - 100, this.height - 32, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("where_winds_blow.config.subtitle"), this.width / 2, 30, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
