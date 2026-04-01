package org.narwhals.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Drives the {@link DoubleJumpPhase} state machine and applies jump-key double jump after input is processed.
 * Runs {@linkplain Order#AFTER after} {@link PlayerSystems.ProcessPlayerInput}.
 */
public class DoubleJumpSystem extends EntityTickingSystem<EntityStore> {
    private final ComponentType<EntityStore, DoubleJumpComponent> djType;

    public DoubleJumpSystem(ComponentType<EntityStore, DoubleJumpComponent> djType) {
        this.djType = djType;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, PlayerSystems.ProcessPlayerInput.class));
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return djType;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> cmd
    ) {
        DoubleJumpComponent dj = chunk.getComponent(index, djType);
        DoubleJumpConfig cfg = DoubleJumpConfig.get();
        MovementStatesComponent msc = chunk.getComponent(index, MovementStatesComponent.getComponentType());
        if (dj == null || cfg == null || msc == null) {
            return;
        }

        MovementStates st = msc.getMovementStates();
        boolean grounded = st.onGround || st.inFluid || st.climbing;

        if (grounded) {
            dj.phase = DoubleJumpPhase.GROUNDED;
            dj.jumpCount = 0;
            dj.prevJumping = st.jumping;
            dj.airJumpPressPending = false;
            return;
        }

        if (dj.phase == DoubleJumpPhase.GROUNDED) {
            dj.phase = DoubleJumpPhase.AIR_CAN_DOUBLE;
        }

        if (!cfg.infiniteDoubleJump && dj.jumpCount >= cfg.maxJumps) {
            dj.phase = DoubleJumpPhase.AIR_SPENT;
        }

        if (ActivationMode.from(cfg) == ActivationMode.JUMP_KEY) {
            if (dj.airJumpPressPending) {
                dj.airJumpPressPending = false;
                if (dj.phase == DoubleJumpPhase.AIR_CAN_DOUBLE) {
                    Ref<EntityStore> ref = chunk.getReferenceTo(index);
                    DoubleJumpExecutor.tryApply(ref, cmd, dj, cfg);
                }
            }
            dj.prevJumping = st.jumping;
        } else {
            dj.prevJumping = st.jumping;
        }
    }
}
