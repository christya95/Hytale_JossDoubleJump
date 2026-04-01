package ca.joss.jossdoublejump;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Runs <strong>before</strong> {@link PlayerSystems.ProcessPlayerInput} so we can still read {@link PlayerInput#getMovementUpdateQueue()}.
 * Detects a jump-button <em>press</em> (false {@code ->} true on {@link MovementStates#jumping}) while already airborne in the queued
 * packets. Sets {@link DoubleJumpComponent#airJumpPressPending}; {@link DoubleJumpSystem} only consumes it in {@link DoubleJumpPhase#AIR_CAN_DOUBLE}.
 */
public final class DoubleJumpQueueScannerSystem extends EntityTickingSystem<EntityStore> {
    private final ComponentType<EntityStore, DoubleJumpComponent> djType;

    public DoubleJumpQueueScannerSystem(ComponentType<EntityStore, DoubleJumpComponent> djType) {
        this.djType = djType;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, PlayerSystems.ProcessPlayerInput.class));
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), PlayerInput.getComponentType(), djType);
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> cmd
    ) {
        DoubleJumpConfig cfg = DoubleJumpConfig.get();
        if (cfg == null || ActivationMode.from(cfg) != ActivationMode.JUMP_KEY) {
            return;
        }
        DoubleJumpComponent dj = chunk.getComponent(index, djType);
        MovementStatesComponent msc = chunk.getComponent(index, MovementStatesComponent.getComponentType());
        PlayerInput input = chunk.getComponent(index, PlayerInput.getComponentType());
        if (dj == null || msc == null || input == null) {
            return;
        }
        List<PlayerInput.InputUpdate> queue = input.getMovementUpdateQueue();
        if (queue == null || queue.isEmpty()) {
            return;
        }

        MovementStates cur = new MovementStates(msc.getMovementStates());
        boolean curJump = cur.jumping;
        for (PlayerInput.InputUpdate update : queue) {
            if (update instanceof PlayerInput.SetMovementStates sms) {
                MovementStates n = sms.movementStates();
                boolean wasAirborne = !(cur.onGround || cur.inFluid || cur.climbing);
                if (wasAirborne && n.jumping && !curJump && dj.phase != DoubleJumpPhase.AIR_SPENT) {
                    dj.airJumpPressPending = true;
                }
                cur = new MovementStates(n);
                curJump = n.jumping;
            }
        }
    }
}
