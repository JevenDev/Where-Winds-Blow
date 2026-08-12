package com.jvn.wherewindsblow.client.foliage;

enum ResponsiveFoliageType {
    NONE(false, false, false, false),
    GRASS(false, false, false, false),
    FOLIAGE(false, false, false, false),
    CROP(false, false, false, false),
    TALL_FOLIAGE(false, true, false, false),
    RIGID_COLUMN(false, true, false, true),
    HANGING_FOLIAGE(false, true, true, false),
    LEAVES(true, false, false, false);

    private final boolean leaves;
    private final boolean column;
    private final boolean hangsFromTop;
    private final boolean coherentColumnMotion;

    ResponsiveFoliageType(boolean leaves, boolean column, boolean hangsFromTop, boolean coherentColumnMotion) {
        this.leaves = leaves;
        this.column = column;
        this.hangsFromTop = hangsFromTop;
        this.coherentColumnMotion = coherentColumnMotion;
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

    boolean usesCoherentColumnMotion() {
        return coherentColumnMotion;
    }
}
