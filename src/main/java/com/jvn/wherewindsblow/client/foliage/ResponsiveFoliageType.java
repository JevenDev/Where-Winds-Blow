package com.jvn.wherewindsblow.client.foliage;

enum ResponsiveFoliageType {
    NONE(false, false, false),
    GRASS(false, false, false),
    FOLIAGE(false, false, false),
    CROP(false, false, false),
    TALL_FOLIAGE(false, true, false),
    HANGING_FOLIAGE(false, true, true),
    LEAVES(true, false, false);

    private final boolean leaves;
    private final boolean column;
    private final boolean hangsFromTop;

    ResponsiveFoliageType(boolean leaves, boolean column, boolean hangsFromTop) {
        this.leaves = leaves;
        this.column = column;
        this.hangsFromTop = hangsFromTop;
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
}
