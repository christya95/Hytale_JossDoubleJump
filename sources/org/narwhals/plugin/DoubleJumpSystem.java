package org.narwhals.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Resets jump state on landing; on {@link ActivationMode#JUMP_KEY}, runs double-jump on fresh jump press in air. */
public class DoubleJumpSystem extends EntityTickingSystem<EntityStore> {
    private final ComponentType<EntityStore, DoubleJumpComponent> djType;

    public DoubleJumpSystem(ComponentType<EntityStore, DoubleJumpComponent> djType) {
        this.djType = djType;
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
            dj.jumpCount = 0;
            dj.prevJumping = st.jumping;
            return;
        }

        if (ActivationMode.from(cfg) == ActivationMode.JUMP_KEY) {
            boolean edge = st.jumping && !dj.prevJumping;
            dj.prevJumping = st.jumping;
            if (edge) {
                DoubleJumpExecutor.tryApply(chunk.getReferenceTo(index), cmd, dj, cfg);
            }
        } else {
            dj.prevJumping = st.jumping;
        }
    }
}
