package com.jvn.wherewindsblow.block;

import net.minecraft.util.StringRepresentable;

public enum OvergrownGrassPart implements StringRepresentable {
    LOWER("lower"),
    MIDDLE("middle"),
    UPPER("upper");

    private final String name;

    OvergrownGrassPart(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}