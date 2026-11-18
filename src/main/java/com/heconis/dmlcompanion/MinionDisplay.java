package com.heconis.dmlcompanion;

import net.minecraftforge.common.ForgeConfigSpec;

final class MinionDisplay {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    static final ForgeConfigSpec.EnumValue<Mode> MODE = BUILDER
            .comment("Display mode for dragon minions on this client")
            .defineEnum("minionDisplay", Mode.FOLLOW);
    static final ForgeConfigSpec SPEC = BUILDER.build();

    private MinionDisplay() {}

    enum Mode { FOLLOW, SHOULDER, HIDDEN }
}
