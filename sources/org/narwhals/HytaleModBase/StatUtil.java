/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
 *  com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType
 */
package org.narwhals.HytaleModBase;

import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;

public final class StatUtil {
    private static volatile int STAMINA_INDEX = -1;
    private static volatile int HEALTH_INDEX = -1;
    private static volatile int SIGNATURE_ENERGY_INDEX = -1;
    private static volatile int STAMINA_REGEN_DELAY_INDEX = -1;

    private StatUtil() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static int staminaIndex() {
        if (STAMINA_INDEX != -1) return STAMINA_INDEX;
        Class<StatUtil> clazz = StatUtil.class;
        synchronized (StatUtil.class) {
            if (STAMINA_INDEX != -1) return STAMINA_INDEX;
            STAMINA_INDEX = EntityStatType.getAssetMap().getIndex((Object)"Stamina");
            // ** MonitorExit[var0] (shouldn't be in output)
            return STAMINA_INDEX;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static int healthIndex() {
        if (HEALTH_INDEX != -1) return HEALTH_INDEX;
        Class<StatUtil> clazz = StatUtil.class;
        synchronized (StatUtil.class) {
            if (HEALTH_INDEX != -1) return HEALTH_INDEX;
            HEALTH_INDEX = EntityStatType.getAssetMap().getIndex((Object)"Health");
            // ** MonitorExit[var0] (shouldn't be in output)
            return HEALTH_INDEX;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static int signatureEnergyIndex() {
        if (SIGNATURE_ENERGY_INDEX != -1) return SIGNATURE_ENERGY_INDEX;
        Class<StatUtil> clazz = StatUtil.class;
        synchronized (StatUtil.class) {
            if (SIGNATURE_ENERGY_INDEX != -1) return SIGNATURE_ENERGY_INDEX;
            SIGNATURE_ENERGY_INDEX = DefaultEntityStatTypes.getSignatureEnergy();
            // ** MonitorExit[var0] (shouldn't be in output)
            return SIGNATURE_ENERGY_INDEX;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static int staminaRegenDelayIndex() {
        if (STAMINA_REGEN_DELAY_INDEX != -1) return STAMINA_REGEN_DELAY_INDEX;
        Class<StatUtil> clazz = StatUtil.class;
        synchronized (StatUtil.class) {
            if (STAMINA_REGEN_DELAY_INDEX != -1) return STAMINA_REGEN_DELAY_INDEX;
            STAMINA_REGEN_DELAY_INDEX = EntityStatType.getAssetMap().getIndex((Object)"StaminaRegenDelay");
            // ** MonitorExit[var0] (shouldn't be in output)
            return STAMINA_REGEN_DELAY_INDEX;
        }
    }
}

