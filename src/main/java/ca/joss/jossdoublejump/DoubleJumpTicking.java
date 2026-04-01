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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import ca.joss.jossdoublejump.util.StatUtil;

/**
 * Jump-charge gameplay: liftoff consumes one charge; the next jump press edge (queue or processed) spends the rest and
 * runs {@link #tryApply}. Movement-queue jump edges (before input) back up processed state. Shared apply for jump-key
 * and ability triggers.
 */
final class DoubleJumpTicking {

    /** Consume a queue-detected jump edge in AfterInput within this window (ms) if the boolean was cleared same tick. */
    private static final long QUEUE_JUMP_EDGE_BUFFER_MS = 120L;

    /** Per {@link PlayerInput.InputUpdate} concrete class: no-arg boolean accessor for jump, or empty if none. */
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> JUMP_BOOL_GETTERS = new ConcurrentHashMap<>();

    private static final String[] JUMP_BOOL_METHOD_NAMES = {
        "jumping", "getJumping", "isJumping", "jump", "getJump", "isJump", "wantsJump", "isWantsJump"
    };

    /** Per {@link PlayerInput.InputUpdate} class: instance fields that may carry jump (booleans or nested {@link MovementStates}). */
    private static final ConcurrentHashMap<Class<?>, JumpFieldProbe> JUMP_FIELD_PROBES = new ConcurrentHashMap<>();

    private static final class JumpFieldProbe {
        final Field[] boolJumpFields;
        final Field[] movementStatesFields;

        JumpFieldProbe(Field[] boolJumpFields, Field[] movementStatesFields) {
            this.boolJumpFields = boolJumpFields;
            this.movementStatesFields = movementStatesFields;
        }

        boolean isEmpty() {
            return boolJumpFields.length == 0 && movementStatesFields.length == 0;
        }
    }

    private DoubleJumpTicking() {}

    /**
     * Walk every queued update: some ticks only expose jump through non-{@link PlayerInput.SetMovementStates} types that
     * still provide {@code movementStates()}.
     */
    @Nullable
    private static MovementStates movementStatesFromInputUpdate(PlayerInput.InputUpdate update) {
        if (update instanceof PlayerInput.SetMovementStates sms) {
            return sms.movementStates();
        }
        Class<?> c = update.getClass();
        for (String name : new String[] {"movementStates", "getMovementStates"}) {
            for (boolean declared : new boolean[] {false, true}) {
                try {
                    Method m = declared ? c.getDeclaredMethod(name) : c.getMethod(name);
                    if (declared) {
                        m.setAccessible(true);
                    }
                    Object r = m.invoke(update);
                    if (r instanceof MovementStates) {
                        return (MovementStates) r;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * Jump bit for queue simulation: {@link MovementStates#jumping} when present, else a cached no-arg boolean getter
     * on packets like {@code AbsoluteMovement} (often no {@code movementStates()}).
     *
     * @return {@code null} when this update carries no jump signal we can read
     */
    @Nullable
    private static Boolean jumpBitFromInputUpdate(PlayerInput.InputUpdate update) {
        MovementStates ms = movementStatesFromInputUpdate(update);
        if (ms != null) {
            return ms.jumping;
        }
        Method m = jumpBoolGetterForClass(update.getClass());
        if (m != null) {
            try {
                Object r = m.invoke(update);
                if (r instanceof Boolean) {
                    return (Boolean) r;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return jumpBitFromFields(update);
    }

    /**
     * {@code AbsoluteMovement} / velocity packets often store movement in private fields (no boolean accessors we know by name).
     */
    @Nullable
    private static Boolean jumpBitFromFields(PlayerInput.InputUpdate update) {
        JumpFieldProbe probe = jumpFieldProbeForClass(update.getClass());
        if (probe.isEmpty()) {
            return null;
        }
        for (Field f : probe.boolJumpFields) {
            try {
                Object v = f.get(update);
                if (v instanceof Boolean) {
                    return (Boolean) v;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        for (Field f : probe.movementStatesFields) {
            try {
                Object v = f.get(update);
                if (v instanceof MovementStates) {
                    return ((MovementStates) v).jumping;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static JumpFieldProbe jumpFieldProbeForClass(Class<?> c) {
        return JUMP_FIELD_PROBES.computeIfAbsent(c, DoubleJumpTicking::buildJumpFieldProbe);
    }

    private static JumpFieldProbe buildJumpFieldProbe(Class<?> start) {
        List<Field> bools = new ArrayList<>();
        List<Field> states = new ArrayList<>();
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                Class<?> t = f.getType();
                if (MovementStates.class.isAssignableFrom(t)) {
                    states.add(f);
                } else if ((t == boolean.class || t == Boolean.class)
                    && f.getName().toLowerCase(Locale.ROOT).contains("jump")) {
                    bools.add(f);
                }
            }
        }
        return new JumpFieldProbe(bools.toArray(new Field[0]), states.toArray(new Field[0]));
    }

    @Nullable
    private static Method jumpBoolGetterForClass(Class<?> c) {
        Optional<Method> cached = JUMP_BOOL_GETTERS.get(c);
        if (cached != null) {
            return cached.orElse(null);
        }
        Method found = findJumpBoolGetter(c);
        JUMP_BOOL_GETTERS.put(c, Optional.ofNullable(found));
        return found;
    }

    @Nullable
    private static Method findJumpBoolGetter(Class<?> c) {
        for (String name : JUMP_BOOL_METHOD_NAMES) {
            for (boolean declared : new boolean[] {false, true}) {
                try {
                    Method m = declared ? c.getDeclaredMethod(name) : c.getMethod(name);
                    if (m.getParameterCount() != 0) {
                        continue;
                    }
                    Class<?> rt = m.getReturnType();
                    if (rt != boolean.class && rt != Boolean.class) {
                        continue;
                    }
                    if (declared) {
                        m.setAccessible(true);
                    }
                    return m;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

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
        if (move == null) {
            DoubleJumpTrace.log(ref, commandBuffer, "tryApply: blocked — no MovementStatesComponent");
            return false;
        }
        if (!airborne(move.getMovementStates())) {
            DoubleJumpTrace.log(ref, commandBuffer, "tryApply: blocked — not airborne (onGround/fluid/climb)");
            return false;
        }
        if (!config.infiniteDoubleJump && dj.chargesRemaining <= 0) {
            DoubleJumpTrace.log(ref, commandBuffer, "tryApply: blocked — no jump charges left");
            return false;
        }
        if (config.cooldownMs > 0L && nowMs - dj.lastDoubleJumpTimeMs < config.cooldownMs) {
            DoubleJumpTrace.log(ref, commandBuffer, "tryApply: blocked — cooldown");
            return false;
        }

        EntityStatMap stats = (EntityStatMap) commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue stamina = stats != null ? stats.get(StatUtil.staminaIndex()) : null;
        float staminaCost = stamina == null ? 0f : staminaCost(config, stamina);
        if (staminaCost > 0f && stamina.get() < staminaCost) {
            DoubleJumpTrace.log(
                ref,
                commandBuffer,
                "tryApply: blocked — stamina " + stamina.get() + " < cost " + staminaCost);
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
        if (!config.infiniteDoubleJump) {
            dj.chargesRemaining = Math.max(0, dj.chargesRemaining - 1);
            if (dj.chargesRemaining <= 0) {
                dj.phase = DoubleJumpComponent.Phase.AIR_SPENT;
            }
        }
        playRoll(ref, commandBuffer);
        DoubleJumpTrace.log(
            ref,
            commandBuffer,
            "MOD_DOUBLE_JUMP_APPLIED — mod air jump #" + dj.jumpCount + " chargesLeft=" + dj.chargesRemaining + " impulse=(" + impulse.x + "," + impulse.y + "," + impulse.z + ")");
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

    private static void onGrounded(DoubleJumpComponent dj, @Nullable DoubleJumpConfig cfg) {
        dj.phase = DoubleJumpComponent.Phase.GROUNDED;
        dj.jumpCount = 0;
        dj.pendingQueueJumpEdge = false;
        dj.queueJumpEdgeBufferUntilMs = 0L;
        dj.jumpHeldLastQueue = false;
        dj.hadJumpPressWhileAirborne = false;
        dj.jumpReleasedSinceAirborne = false;
        dj.chargesRemaining = cfg != null && !cfg.infiniteDoubleJump ? cfg.totalJumpCharges() : 0;
    }

    private static void tickAirborne(DoubleJumpComponent dj, @Nullable DoubleJumpConfig cfg) {
        if (cfg == null) {
            return;
        }
        if (dj.phase == DoubleJumpComponent.Phase.GROUNDED) {
            dj.phase = DoubleJumpComponent.Phase.AIR_CAN_DOUBLE;
        }
        if (!cfg.infiniteDoubleJump && dj.chargesRemaining <= 0) {
            dj.phase = DoubleJumpComponent.Phase.AIR_SPENT;
        }
    }

    /**
     * Runs before {@link PlayerSystems.ProcessPlayerInput}: detects jump rising edges in {@link PlayerInput#getMovementUpdateQueue()}.
     * Processed {@link MovementStatesComponent#getMovementStates()}{@code .jumping} often stays false for mid-air presses,
     * so {@link AfterInputSystem} also consumes {@link DoubleJumpComponent#pendingQueueJumpEdge}.
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
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            DoubleJumpComponent dj = chunk.getComponent(index, djType);
            MovementStatesComponent msc = chunk.getComponent(index, MovementStatesComponent.getComponentType());
            PlayerInput input = chunk.getComponent(index, PlayerInput.getComponentType());
            if (dj == null || msc == null || input == null) {
                return;
            }
            dj.pendingQueueJumpEdge = false;
            List<PlayerInput.InputUpdate> queue = input.getMovementUpdateQueue();
            if (queue == null || queue.isEmpty()) {
                return;
            }

            long nowMs =
                ((TimeResource) cmd.getResource(TimeResource.getResourceType())).getNow().toEpochMilli();

            if (DoubleJumpTrace.is(ref, cmd)) {
                StringBuilder sb = new StringBuilder(256);
                sb.append("movementQueue: size=").append(queue.size()).append(" simPhase=").append(dj.phase);
                for (PlayerInput.InputUpdate update : queue) {
                    Boolean jb = jumpBitFromInputUpdate(update);
                    sb.append(" | ").append(update.getClass().getSimpleName());
                    if (jb != null) {
                        sb.append("(j=").append(jb).append(")");
                    } else {
                        sb.append("(noBit)");
                    }
                }
                DoubleJumpTrace.log(ref, cmd, sb.toString());
            }

            MovementStates startMs = msc.getMovementStates();
            boolean serverAirborne = airborne(startMs);

            // Always advance queue simulation from last tick's end state (not raw msc — it can disagree with this queue).
            boolean prevQJump = dj.jumpHeldLastQueue;
            boolean curQJump = prevQJump;
            boolean risingEdge = false;
            for (PlayerInput.InputUpdate update : queue) {
                Boolean j = jumpBitFromInputUpdate(update);
                if (j == null) {
                    continue;
                }
                if (j && !curQJump) {
                    risingEdge = true;
                }
                curQJump = j;
            }
            dj.jumpHeldLastQueue = curQJump;

            if (!serverAirborne) {
                return;
            }

            if (!cfg.infiniteDoubleJump && (dj.chargesRemaining <= 0 || dj.phase == DoubleJumpComponent.Phase.AIR_SPENT)) {
                return;
            }

            if (!dj.jumpReleasedSinceAirborne) {
                if (DoubleJumpTrace.is(ref, cmd)) {
                    DoubleJumpTrace.log(
                        ref,
                        cmd,
                        "queueScan: skip edge (not releasedSinceAir yet) prevQJump=" + prevQJump + " endQJump=" + curQJump + " risingEdge=" + risingEdge);
                }
                return;
            }

            boolean netEdge = curQJump && !prevQJump;
            if (risingEdge || netEdge) {
                dj.pendingQueueJumpEdge = true;
                dj.queueJumpEdgeBufferUntilMs = nowMs + QUEUE_JUMP_EDGE_BUFFER_MS;
                DoubleJumpTrace.log(
                    ref,
                    cmd,
                    "queueScan: airborne phase=" + dj.phase + " prevQJump=" + prevQJump + " endQJump=" + curQJump
                        + " risingEdge=" + risingEdge + " netEdge=" + netEdge + " releasedSinceAir=true -> pending+buffer "
                        + QUEUE_JUMP_EDGE_BUFFER_MS + "ms");
            } else if (DoubleJumpTrace.is(ref, cmd)) {
                DoubleJumpTrace.log(
                    ref,
                    cmd,
                    "queueScan: airborne phase=" + dj.phase + " prevQJump=" + prevQJump + " endQJump=" + curQJump
                        + " risingEdge=false netEdge=false releasedSinceAir=true");
            }
        }
    }

    /**
     * After {@link PlayerSystems.ProcessPlayerInput}: phase transitions and jump-key trigger from processed movement
     * and/or {@link DoubleJumpComponent#pendingQueueJumpEdge}.
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

            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            MovementStates st = msc.getMovementStates();
            boolean grounded = st.onGround || st.inFluid || st.climbing;

            if (grounded) {
                dj.jumpPressedLastAfterInput = st.jumping;
                onGrounded(dj, cfg);
                return;
            }

            boolean liftoffTick = dj.phase == DoubleJumpComponent.Phase.GROUNDED;
            if (!cfg.infiniteDoubleJump && liftoffTick) {
                dj.chargesRemaining = Math.max(0, dj.chargesRemaining - 1);
            }

            boolean prevJumpAfterInput = dj.jumpPressedLastAfterInput;
            if (st.jumping) {
                dj.hadJumpPressWhileAirborne = true;
            } else if (dj.hadJumpPressWhileAirborne) {
                dj.jumpReleasedSinceAirborne = true;
            }

            tickAirborne(dj, cfg);

            long nowMs =
                ((TimeResource) cmd.getResource(TimeResource.getResourceType())).getNow().toEpochMilli();
            boolean queueEdge =
                dj.pendingQueueJumpEdge || (dj.queueJumpEdgeBufferUntilMs != 0L && nowMs < dj.queueJumpEdgeBufferUntilMs);
            boolean processedEdge = st.jumping && !prevJumpAfterInput;

            DoubleJumpTrace.log(
                ref,
                cmd,
                "afterInput: phase=" + dj.phase + " charges=" + dj.chargesRemaining + " liftoffTick=" + liftoffTick
                    + " releasedSinceAir=" + dj.jumpReleasedSinceAirborne + " prevJump=" + prevJumpAfterInput
                    + " queueEdge=" + queueEdge + " procEdge=" + processedEdge
                    + " move(jump=" + st.jumping + ",og=" + st.onGround + ",fluid=" + st.inFluid + ",climb=" + st.climbing + ")");

            if (DoubleJumpConfig.ActivationMode.from(cfg) == DoubleJumpConfig.ActivationMode.JUMP_KEY
                && dj.phase == DoubleJumpComponent.Phase.AIR_CAN_DOUBLE
                && !liftoffTick
                && dj.jumpReleasedSinceAirborne
                && (queueEdge || processedEdge)
                && (cfg.infiniteDoubleJump || dj.chargesRemaining > 0)) {
                if (tryApply(ref, cmd, dj, cfg)) {
                    dj.queueJumpEdgeBufferUntilMs = 0L;
                }
            }

            dj.pendingQueueJumpEdge = false;
            dj.jumpPressedLastAfterInput = st.jumping;
            // Next tick's queue baseline should match post-input jump as well as queue simulation (often diverge).
            dj.jumpHeldLastQueue = dj.jumpHeldLastQueue || st.jumping;
        }
    }
}
