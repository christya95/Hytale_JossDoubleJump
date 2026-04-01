/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.hypixel.hytale.logger.HytaleLogger
 *  com.hypixel.hytale.logger.HytaleLogger$Api
 */
package ca.joss.jossdoublejump;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.File;

public final class DoubleJumpConfig {
    private static final HytaleLogger LOGGER = DoubleJumpLogger.get();
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

    public static void load(File file, Gson gson) {
        configDir = file;
        DoubleJumpConfig.gson = gson;
        instance = DoubleJump.getConfigLoader().load(DoubleJumpConfig.class, "double_jump_config.json", "double_jump_defaults.json", file, gson);
        ((HytaleLogger.Api)LOGGER.atInfo()).log("Loaded successfully");
    }

    public static void saveToDisk() {
        DoubleJump.getConfigLoader().save(new File(configDir, "double_jump_config.json"), instance, gson);
    }

    public static DoubleJumpConfig get() {
        return instance;
    }
}

