/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.hypixel.hytale.component.AddReason
 *  com.hypixel.hytale.component.CommandBuffer
 *  com.hypixel.hytale.component.Component
 *  com.hypixel.hytale.component.ComponentType
 *  com.hypixel.hytale.component.Ref
 *  com.hypixel.hytale.component.RemoveReason
 *  com.hypixel.hytale.component.Store
 *  com.hypixel.hytale.component.query.Query
 *  com.hypixel.hytale.component.system.RefSystem
 *  com.hypixel.hytale.logger.HytaleLogger
 *  com.hypixel.hytale.logger.HytaleLogger$Api
 *  com.hypixel.hytale.server.core.universe.PlayerRef
 *  com.hypixel.hytale.server.core.universe.world.storage.EntityStore
 *  javax.annotation.Nonnull
 */
package org.narwhals.plugin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import org.narwhals.plugin.DoubleJumpComponent;
import org.narwhals.plugin.DoubleJumpLogger;

public class PlayerJoinDoubleJumpAdder
extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = DoubleJumpLogger.get();
    private final ComponentType<EntityStore, DoubleJumpComponent> doubleJumpComponentType;

    public PlayerJoinDoubleJumpAdder(ComponentType<EntityStore, DoubleJumpComponent> componentType) {
        this.doubleJumpComponentType = componentType;
    }

    @Nonnull
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason addReason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (commandBuffer.getComponent(ref, this.doubleJumpComponentType) == null) {
            commandBuffer.addComponent(ref, this.doubleJumpComponentType, (Component)new DoubleJumpComponent());
            ((HytaleLogger.Api)LOGGER.atFine()).log("Added DoubleJumpComponent to player");
        } else {
            ((HytaleLogger.Api)LOGGER.atFine()).log("Resetting state for world transfer");
            DoubleJumpComponent doubleJumpComponent = (DoubleJumpComponent)commandBuffer.getComponent(ref, this.doubleJumpComponentType);
            if (doubleJumpComponent != null) {
                doubleJumpComponent.jumpCount = 0;
                doubleJumpComponent.lastDoubleJumpTimeMs = 0L;
                doubleJumpComponent.prevJumping = false;
            }
        }
    }

    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason removeReason, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }
}

