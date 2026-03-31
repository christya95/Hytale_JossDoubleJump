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

public class DoubleJumpComponent
implements Component<EntityStore> {
    int jumpCount;
    long lastDoubleJumpTimeMs;
    boolean prevJumping;

    @Nonnull
    public DoubleJumpComponent clone() {
        DoubleJumpComponent doubleJumpComponent = new DoubleJumpComponent();
        doubleJumpComponent.jumpCount = this.jumpCount;
        doubleJumpComponent.lastDoubleJumpTimeMs = this.lastDoubleJumpTimeMs;
        doubleJumpComponent.prevJumping = this.prevJumping;
        return doubleJumpComponent;
    }
}

