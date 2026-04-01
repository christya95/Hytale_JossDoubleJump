package ca.joss.jossdoublejump;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.entities.PlayAnimation;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.PlayerUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import ca.joss.jossdoublejump.util.StatUtil;

/**
 * Double-jump gameplay: movement-queue scan (before input), phase updates + consume (after input),
 * and shared apply logic for jump-key and ability triggers.
 */
final class DoubleJumpTicking {

    private DoubleJumpTicking() {}

    static boolean tryApply(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull DoubleJumpComponent dj,
        @Nonnull DoubleJumpConfig config
    ) {
        long nowMs =
            ((TimeResource) commandBuffer.getResource(TimeResource.getResourceType())).getNow().toEpochMilli();

        MovementStatesComponent move =
            (MovementStatesComponent) commandBuffer.getComponent(ref, MovementStatesComponent.getComponentType());
        if (move != null && !airborne(move.getMovementStates())) {
            return false;
        }
        if (!config.infiniteDoubleJump && dj.jumpCount >= config.maxJumps) {
            return false;
        }
        if (config.cooldownMs > 0L && nowMs - dj.lastDoubleJumpTimeMs < config.cooldownMs) {
            return false;
        }

        EntityStatMap stats = (EntityStatMap) commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue stamina = stats != null ? stats.get(StatUtil.staminaIndex()) : null;
        float staminaCost = stamina == null ? 0f : staminaCost(config, stamina);
        if (staminaCost > 0f && stamina.get() < staminaCost) {
            return false;
        }

        if (staminaCost > 0f) {
            stats.subtractStatValue(StatUtil.staminaIndex(), staminaCost);
        }

        float yaw = yaw(commandBuffer, ref);
        double hx = -Math.sin(yaw);
        double hz = -Math.cos(yaw);
        Vector3d impulse = new Vector3d(hx * config.horizontalBoost, config.verticalBoost, hz * config.horizontalBoost);

        Velocity v = (Velocity) commandBuffer.getComponent(ref, Velocity.getComponentType());
        if (v != null) {
            v.getInstructions().clear();
            v.addInstruction(impulse, null, ChangeVelocityType.Set);
        }

        dj.jumpCount++;
        dj.lastDoubleJumpTimeMs = nowMs;
        if (!config.infiniteDoubleJump && dj.jumpCount >= config.maxJumps) {
            dj.phase = DoubleJumpComponent.Phase.AIR_SPENT;
        }
        playRoll(ref, commandBuffer);
        return true;
    }

    private static boolean airborne(MovementStates s) {
        return !s.onGround && !s.inFluid && !s.climbing;
    }

    private static float staminaCost(DoubleJumpConfig config, EntityStatValue stamina) {
        return config.usePercentageStamina
            ? stamina.getMax() * (config.staminaLossPercentage / 100.0f)
            : config.staminaCost;
    }

    private static float yaw(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ref) {
        TransformComponent t = (TransformComponent) commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (t == null) {
            return 0f;
        }
        ModelTransform sent = t.getSentTransform();
        return sent.lookOrientation != null ? sent.lookOrientation.yaw : t.getRotation().y;
    }

    private static void playRoll(Ref<EntityStore> ref, CommandBuffer<EntityStore> commandBuffer) {
        NetworkId net = (NetworkId) commandBuffer.getComponent(ref, NetworkId.getComponentType());
        if (net == null) {
            return;
        }
        int id = net.getId();
        PlayAnimation clear = new PlayAnimation(id, null, "", AnimationSlot.Action);
        PlayAnimation roll = new PlayAnimation(id, null, "Roll", AnimationSlot.Action);
        PlayerUtil.forEachPlayerThatCanSeeEntity(
            ref,
            (e, player, acc) -> {
                player.getPacketHandler().writeNoCache((ToClientPacket) clear);
                player.getPacketHandler().writeNoCache((ToClientPacket) roll);
            },
            (ComponentAccessor<EntityStore>) commandBuffer);
        PlayerRef self = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (self != null) {
            self.getPacketHandler().writeNoCache((ToClientPacket) clear);
            self.getPacketHandler().writeNoCache((ToClientPacket) roll);
        }
    }

    private static void onGrounded(DoubleJumpComponent dj) {
        dj.phase = DoubleJumpComponent.Phase.GROUNDED;
        dj.jumpCount = 0;
        dj.airJumpPressPending = false;
    }

    private static void tickAirborne(DoubleJumpComponent dj, @Nullable DoubleJumpConfig cfg) {
        if (cfg == null) {
            return;
        }
        if (dj.phase == DoubleJumpComponent.Phase.GROUNDED) {
            dj.phase = DoubleJumpComponent.Phase.AIR_CAN_DOUBLE;
        }
        if (!cfg.infiniteDoubleJump && dj.jumpCount >= cfg.maxJumps) {
            dj.phase = DoubleJumpComponent.Phase.AIR_SPENT;
        }
    }

    private static boolean consumeAirJumpPressIfPending(
        Ref<EntityStore> ref,
        CommandBuffer<EntityStore> cmd,
        DoubleJumpComponent dj,
        DoubleJumpConfig cfg
    ) {
        if (!dj.airJumpPressPending) {
            return false;
        }
        dj.airJumpPressPending = false;
        if (dj.phase != DoubleJumpComponent.Phase.AIR_CAN_DOUBLE) {
            return false;
        }
        return tryApply(ref, cmd, dj, cfg);
    }

    /**
     * Runs before {@link PlayerSystems.ProcessPlayerInput} to read {@link PlayerInput#getMovementUpdateQueue()} for jump
     * press edges while airborne.
     */
    static final class QueueScannerSystem extends EntityTickingSystem<EntityStore> {
        private final ComponentType<EntityStore, DoubleJumpComponent> djType;

        QueueScannerSystem(ComponentType<EntityStore, DoubleJumpComponent> djType) {
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
            if (cfg == null || DoubleJumpConfig.ActivationMode.from(cfg) != DoubleJumpConfig.ActivationMode.JUMP_KEY) {
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
                    if (wasAirborne && n.jumping && !curJump && dj.phase != DoubleJumpComponent.Phase.AIR_SPENT) {
                        dj.airJumpPressPending = true;
                    }
                    cur = new MovementStates(n);
                    curJump = n.jumping;
                }
            }
        }
    }

    /**
     * After {@link PlayerSystems.ProcessPlayerInput}: phase transitions and consuming queued jump presses.
     */
    static final class AfterInputSystem extends EntityTickingSystem<EntityStore> {
        private final ComponentType<EntityStore, DoubleJumpComponent> djType;

        AfterInputSystem(ComponentType<EntityStore, DoubleJumpComponent> djType) {
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
                onGrounded(dj);
                return;
            }

            tickAirborne(dj, cfg);

            if (DoubleJumpConfig.ActivationMode.from(cfg) == DoubleJumpConfig.ActivationMode.JUMP_KEY) {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                consumeAirJumpPressIfPending(ref, cmd, dj, cfg);
            }
        }
    }
}
