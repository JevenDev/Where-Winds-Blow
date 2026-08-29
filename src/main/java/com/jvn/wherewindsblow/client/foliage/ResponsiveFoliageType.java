package com.jvn.wherewindsblow.client.foliage;

enum ResponsiveFoliageType {
    NONE(false, false, false, false),
    GRASS(false, false, false, true),
    FLOWER(false, false, false, true),
    FOLIAGE(false, false, false, true),
    CROP(false, false, false, true),
    TALL_FOLIAGE(false, true, false, true),
    RIGID_COLUMN(false, true, false, true),
    HANGING_FOLIAGE(false, true, true, true),
    LEAVES(true, false, false, false);

    private final boolean leaves;
    private final boolean column;
    private final boolean hangsFromTop;
    private final boolean coherentCutoutMotion;

    ResponsiveFoliageType(boolean leaves, boolean column, boolean hangsFromTop, boolean coherentCutoutMotion) {
        this.leaves = leaves;
        this.column = column;
        this.hangsFromTop = hangsFromTop;
        this.coherentCutoutMotion = coherentCutoutMotion;
    }

    boolean isLeaves() {
        return leaves;
    }

    boolean isPlant() {
        return this != NONE && !leaves;
    }

    boolean isColumn() {
        return column;
    }

    boolean hangsFromTop() {
        return hangsFromTop;
    }

    boolean usesCoherentCutoutMotion() {
        return coherentCutoutMotion;
    }
}
