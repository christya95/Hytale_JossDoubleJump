/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.hypixel.hytale.logger.HytaleLogger
 */
package ca.joss.jossdoublejump;

import com.hypixel.hytale.logger.HytaleLogger;

public final class DoubleJumpLogger {
    private static final HytaleLogger INSTANCE = HytaleLogger.get((String)"DoubleJump");

    private DoubleJumpLogger() {
    }

    public static HytaleLogger get() {
        return INSTANCE;
    }
}

