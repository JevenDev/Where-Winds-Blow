package com.jvn.wherewindsblow.client.wind;

import com.jvn.wherewindsblow.client.banner.BannerWindStateCache;
import com.jvn.wherewindsblow.config.ClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
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
        List<String> lines = new ArrayList<>(List.of(
                "Wind · " + modeName(),
                String.format(Locale.ROOT, "Direction  %3.0f°", state.directionDegrees()),
                String.format(Locale.ROOT, "Strength   %.2f", sample.strength()),
                String.format(Locale.ROOT, "Ambient    %.2f", sample.ambientStrength()),
                String.format(Locale.ROOT, "Gust       %.2f", sample.gustStrength()),
                String.format(Locale.ROOT, "Turbulence %.2f", sample.turbulence()),
                String.format(Locale.ROOT, "Exposure   %.2f", sample.exposure()),
                "Profile    " + sample.profileId().getPath(),
                "Gust fronts " + DynamicWindManager.activeGustCount(),
                "Banner states " + BannerWindStateCache.stateCount()
        ));
        if (minecraft.hitResult instanceof BlockHitResult hit
                && minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof BannerBlockEntity banner) {
            boolean wall = banner.getBlockState().getBlock() instanceof WallBannerBlock;
            BannerWindStateCache.State bannerState = BannerWindStateCache.debugState(hit.getBlockPos(), wall);
            if (bannerState != null) {
                lines.add(wall ? "Wall banner" : "Standing banner");
                lines.add(String.format(Locale.ROOT, "Extension  %.2f", bannerState.extension(1.0F)));
                lines.add(String.format(Locale.ROOT, "Crosswind  %.2f", bannerState.crosswind(1.0F)));
                lines.add(String.format(Locale.ROOT, "Banner gust %.2f", bannerState.gust(1.0F)));
                lines.add(String.format(Locale.ROOT, "Banner turb %.2f", bannerState.turbulence(1.0F)));
                lines.add(String.format(Locale.ROOT, "Banner exp  %.2f", bannerState.exposure(1.0F)));
            }
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }

        int boxWidth = width + PADDING * 2;
        int boxHeight = lines.size() * font.lineHeight + PADDING * 2;
        int x = graphics.guiWidth() - boxWidth - 6;
        int y = 6;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND_COLOR);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font, lines.get(index), x + PADDING, y + PADDING + index * font.lineHeight, TEXT_COLOR, false);
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
