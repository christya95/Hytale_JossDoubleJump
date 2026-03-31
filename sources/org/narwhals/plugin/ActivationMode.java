package org.narwhals.plugin;

import javax.annotation.Nullable;

/** Resolved from {@link DoubleJumpConfig} booleans (jump key wins over ability flags). */
public enum ActivationMode {
    JUMP_KEY,
    ABILITY_2,
    ABILITY_3,
    NONE;

    public static ActivationMode from(@Nullable DoubleJumpConfig c) {
        if (c == null) {
            return NONE;
        }
        if (c.useJumpKey) {
            return JUMP_KEY;
        }
        if (c.useAbility2) {
            return ABILITY_2;
        }
        if (c.useAbility3) {
            return ABILITY_3;
        }
        return NONE;
    }

    public String uiLabel() {
        return switch (this) {
            case JUMP_KEY -> "Jump (air)";
            case ABILITY_2 -> "Ability 2";
            case ABILITY_3 -> "Ability 3";
            case NONE -> "None";
        };
    }

    /** True when the legacy “inject Root_DoubleJump into item abilities” path should run. */
    public boolean usesAbilityInjection() {
        return this == ABILITY_2 || this == ABILITY_3;
    }
}
