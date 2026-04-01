package ca.joss.jossdoublejump;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.File;
import javax.annotation.Nullable;

public final class DoubleJumpConfig {
    private static final HytaleLogger LOGGER = Log.INSTANCE;
    private static DoubleJumpConfig instance;
    private static File configDir;
    private static Gson gson;
    public float horizontalBoost;
    public float verticalBoost;
    public long cooldownMs;
    public float staminaCost;
    public boolean usePercentageStamina;
    public float staminaLossPercentage;
    public boolean infiniteDoubleJump;
    public int maxJumps;
    public boolean useAbility2;
    public boolean useAbility3;
    public boolean useJumpKey;

    /** Resolved from config booleans (jump key wins over ability flags). */
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

        public boolean usesAbilityInjection() {
            return this == ABILITY_2 || this == ABILITY_3;
        }
    }

    public static void load(File file, Gson gson) {
        configDir = file;
        DoubleJumpConfig.gson = gson;
        instance = DoubleJump.getConfigLoader().load(DoubleJumpConfig.class, "double_jump_config.json", "double_jump_defaults.json", file, gson);
        ((HytaleLogger.Api) LOGGER.atInfo()).log("Loaded successfully");
    }

    public static void saveToDisk() {
        DoubleJump.getConfigLoader().save(new File(configDir, "double_jump_config.json"), instance, gson);
    }

    public static DoubleJumpConfig get() {
        return instance;
    }
}
