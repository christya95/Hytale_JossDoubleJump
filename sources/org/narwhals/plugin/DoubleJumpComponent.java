/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.hypixel.hytale.component.Component
 *  com.hypixel.hytale.server.core.universe.world.storage.EntityStore
 *  javax.annotation.Nonnull
 */
package org.narwhals.plugin;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class DoubleJumpComponent implements Component<EntityStore> {
    /** Extra mod jumps used this airtime; compared to {@link DoubleJumpConfig#maxJumps} in {@link DoubleJumpExecutor}. */
    int jumpCount;
    long lastDoubleJumpTimeMs;
    boolean prevJumping;

    DoubleJumpPhase phase = DoubleJumpPhase.GROUNDED;

    /**
     * Set by {@link DoubleJumpQueueScannerSystem} when a jump press edge appears in the raw movement queue while simulated
     * airborne. Consumed by {@link DoubleJumpSystem}.
     */
    boolean airJumpPressPending;

    @Nonnull
    public DoubleJumpComponent clone() {
        DoubleJumpComponent c = new DoubleJumpComponent();
        c.jumpCount = this.jumpCount;
        c.lastDoubleJumpTimeMs = this.lastDoubleJumpTimeMs;
        c.prevJumping = this.prevJumping;
        c.phase = this.phase;
        c.airJumpPressPending = this.airJumpPressPending;
        return c;
    }
}
