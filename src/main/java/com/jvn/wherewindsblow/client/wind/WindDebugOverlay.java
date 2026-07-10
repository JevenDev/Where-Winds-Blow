package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class WindDebugOverlay {
    private static final int TEXT_COLOR = 0xffe7edf2;
    private static final int BACKGROUND_COLOR = 0xa010151a;
    private static final int PADDING = 4;

    private WindDebugOverlay() {
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientConfig.ENABLE_WIND_DEBUG_OVERLAY.getAsBoolean()
                || minecraft.level == null
                || minecraft.player == null
                || minecraft.options.hideGui) {
            return;
        }

        GlobalWindState state = DynamicWindManager.currentState();
        WindSample sample = DynamicWindManager.sampleWind(minecraft.player.blockPosition());
        String[] lines = {
                "Wind · " + modeName(),
                String.format(Locale.ROOT, "Direction  %3.0f°", state.directionDegrees()),
                String.format(Locale.ROOT, "Strength   %.2f", sample.strength()),
                String.format(Locale.ROOT, "Ambient    %.2f", sample.ambientStrength()),
                String.format(Locale.ROOT, "Gust       %.2f", sample.gustStrength()),
                String.format(Locale.ROOT, "Turbulence %.2f", sample.turbulence()),
                String.format(Locale.ROOT, "Exposure   %.2f", sample.exposure()),
                "Gust fronts " + DynamicWindManager.activeGustCount()
        };

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }

        int boxWidth = width + PADDING * 2;
        int boxHeight = lines.length * font.lineHeight + PADDING * 2;
        int x = graphics.guiWidth() - boxWidth - 6;
        int y = 6;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND_COLOR);
        for (int index = 0; index < lines.length; index++) {
            graphics.drawString(font, lines[index], x + PADDING, y + PADDING + index * font.lineHeight, TEXT_COLOR, false);
        }
    }

    private static String modeName() {
        if (!ClientConfig.ENABLE_DYNAMIC_WIND.getAsBoolean()) {
            return "Fixed";
        }
        return ClientConfig.WIND_DIRECTION_MODE.get() == ClientConfig.WindDirectionMode.DYNAMIC
                ? "Dynamic"
                : "Fixed";
    }
}
