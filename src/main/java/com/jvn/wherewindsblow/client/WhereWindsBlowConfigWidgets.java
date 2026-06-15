package com.jvn.wherewindsblow.client;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

final class WhereWindsBlowConfigWidgets {
    private static final double SLIDER_STEP = 0.05D;

    private WhereWindsBlowConfigWidgets() {
    }

    static Button booleanButton(int x, int y, ModConfigSpec.BooleanValue value, ModConfigSpec spec) {
        return booleanButton(x, y, value, spec, () -> {
        });
    }

    static Button booleanButton(int x, int y, ModConfigSpec.BooleanValue value, ModConfigSpec spec, Runnable afterChange) {
        String path = value.getPath().getLast();
        Supplier<Component> label = () -> optionLabel(path, value.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
        Button button = Button.builder(label.get(), clickedButton -> {
                    value.set(!value.getAsBoolean());
                    spec.save();
                    afterChange.run();
                    clickedButton.setMessage(label.get());
                })
                .bounds(x, y, 150, 20)
                .build();
        button.setTooltip(optionTooltip(path));
        return button;
    }

    static Button steppedDoubleButton(int x, int y, String key, DoubleSupplier getter, Consumer<Double> setter, double[] values) {
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

    static AbstractSliderButton slider(int x, int y, int width, String path, DoubleSupplier getter, Consumer<Double> setter, double min, double max) {
        return new ConfigSliderButton(x, y, width, path, getter, setter, min, max);
    }

    static void set(ModConfigSpec.DoubleValue value, double newValue) {
        value.set(newValue);
        value.save();
    }

    static Component optionLabel(String path, Component value) {
        return Component.translatable("where_winds_blow.config." + path).append(": ").append(value);
    }

    static Tooltip optionTooltip(String path) {
        return Tooltip.create(Component.translatable("where_winds_blow.config." + path + ".tooltip"));
    }

    static double next(double current, double[] values) {
        for (double value : values) {
            if (value > current + 0.001D) {
                return value;
            }
        }

        return values[0];
    }

    static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static final class ConfigSliderButton extends AbstractSliderButton {
        private final String path;
        private final DoubleSupplier getter;
        private final Consumer<Double> setter;
        private final double min;
        private final double max;

        private ConfigSliderButton(int x, int y, int width, String path, DoubleSupplier getter, Consumer<Double> setter, double min, double max) {
            super(x, y, width, 20, CommonComponents.EMPTY, normalize(getter.getAsDouble(), min, max));
            this.path = path;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.setTooltip(optionTooltip(path));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(optionLabel(this.path, Component.literal(format(this.getter.getAsDouble()))));
        }

        @Override
        protected void applyValue() {
            double snapped = Math.round(this.denormalize(this.value) / SLIDER_STEP) * SLIDER_STEP;
            double clamped = Math.max(this.min, Math.min(this.max, snapped));
            this.setter.accept(clamped);
            this.value = normalize(clamped, this.min, this.max);
            this.updateMessage();
        }

        private double denormalize(double normalized) {
            return this.min + normalized * (this.max - this.min);
        }

        private static double normalize(double value, double min, double max) {
            if (max <= min) {
                return 0.0D;
            }

            return Mth.clamp((value - min) / (max - min), 0.0D, 1.0D);
        }
    }
}
