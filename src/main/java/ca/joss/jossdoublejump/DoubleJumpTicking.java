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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import amore.servercomm.api.ApplyResult;
import ca.joss.jossdoublejump.mixin.PlayerInputQueueMixin;
import ca.joss.jossdoublejump.util.StatUtil;

/**
 * Jump-charge gameplay: liftoff consumes one charge; a cohesive input FSM detects the next jump press request and spends
 * the rest via {@link #tryApply}. Raw {@link PlayerInput} is the primary signal; queue SMS held/edge is fallback.
 */
final class DoubleJumpTicking {

    /** Per concrete PlayerInput class: no-arg boolean accessor for raw jump pressed. */
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> RAW_JUMP_GETTERS = new ConcurrentHashMap<>();
    /** Per concrete PlayerInput class: boolean field that represents raw jump pressed, or empty if none. */
    private static final ConcurrentHashMap<Class<?>, Optional<Field>> RAW_JUMP_FIELDS = new ConcurrentHashMap<>();
    /** Per class: no-arg method returning {@link MovementStates} (client/input buffer), read {@code .jumping}. */
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> RAW_MS_GETTERS = new ConcurrentHashMap<>();
    /** Per class: field of type {@link MovementStates} holding latest client/input movement. */
    private static final ConcurrentHashMap<Class<?>, Optional<Field>> RAW_MS_FIELDS = new ConcurrentHashMap<>();
    /** Cached: all instance no-arg methods returning MovementStates (sorted: likely client/pending first). */
    private static final ConcurrentHashMap<Class<?>, List<Method>> ALL_MS_GETTERS = new ConcurrentHashMap<>();
    /** Cached: all instance fields of type MovementStates (sorted: likely client/pending first). */
    private static final ConcurrentHashMap<Class<?>, List<Field>> ALL_MS_FIELDS_SORTED = new ConcurrentHashMap<>();

    private static final String[] RAW_JUMP_METHOD_NAMES = {
        "isJumpPressed",
        "getJumpPressed",
        "jumpPressed",
        "isJumpDown",
        "getJumpDown",
        "jumpDown",
        "isJumpHeld",
        "getJumpHeld",
        "jumpHeld",
        // Some implementations just expose “jumping”.
        "isJumping",
        "getJumping",
        "jumping",
        "wasJumpPressed",
        "getWasJumpPressed"
    };

    /** Try in order; some builds expose input buffer only via a MovementStates getter. */
    private static final String[] RAW_MS_GETTER_NAMES = {
        "getRawMovementStates",
        "getClientMovementStates",
        "getPendingMovementStates",
        "getInputMovementStates",
        "getQueuedMovementStates",
        "getMovementStates"
    };

    private DoubleJumpTicking() {}

    /**
     * Jump held bit from one queue entry. Non-SMS updates are ignored so the simulated held state only advances when the
     * client sends {@link PlayerInput.SetMovementStates} (matches reliable edges in traces).
     */
    @Nullable
    private static Boolean jumpBitFromQueueUpdate(PlayerInput.InputUpdate update) {
        if (update instanceof PlayerInput.SetMovementStates sms) {
            return sms.movementStates().jumping;
        }
        return null;
    }

    @Nullable
    private static Boolean rawJumpPressedFromInput(PlayerInput input) {
        if (input == null) {
            return null;
        }
        Class<?> cls = input.getClass();
        Method m = rawJumpGetterForClass(cls);
        if (m != null) {
            try {
                Object r = m.invoke(input);
                if (r instanceof Boolean) {
                    return (Boolean) r;
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall through.
            }
        }
        Field boolF = rawJumpFieldForClass(cls);
        if (boolF != null) {
            try {
                Object v = boolF.get(input);
                if (v instanceof Boolean) {
                    return (Boolean) v;
                }
            } catch (IllegalAccessException ignored) {
                // Fall through.
            }
        }
        Boolean fromMsGetter = rawJumpingFromMovementStatesGetter(input);
        if (fromMsGetter != null) {
            return fromMsGetter;
        }
        return rawJumpingFromMovementStatesField(input);
    }

    @Nullable
    private static Boolean rawJumpingFromMovementStatesGetter(PlayerInput input) {
        Method gm = rawMsGetterForClass(input.getClass());
        if (gm == null) {
            return null;
        }
        try {
            Object o = gm.invoke(input);
            if (o instanceof MovementStates ms) {
                return ms.jumping;
            }
            return null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Boolean rawJumpingFromMovementStatesField(PlayerInput input) {
        Field mf = rawMsFieldForClass(input.getClass());
        if (mf == null) {
            return null;
        }
        try {
            Object o = mf.get(input);
            if (o instanceof MovementStates ms) {
                return ms.jumping;
            }
            return null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    @Nullable
    private static Method rawJumpGetterForClass(Class<?> c) {
        Optional<Method> cached = RAW_JUMP_GETTERS.get(c);
        if (cached != null) {
            return cached.orElse(null);
        }
        Method found = findRawJumpGetter(c);
        RAW_JUMP_GETTERS.put(c, Optional.ofNullable(found));
        return found;
    }

    @Nullable
    private static Field rawJumpFieldForClass(Class<?> c) {
        Optional<Field> cached = RAW_JUMP_FIELDS.get(c);
        if (cached != null) {
            return cached.orElse(null);
        }
        Field found = findRawJumpField(c);
        RAW_JUMP_FIELDS.put(c, Optional.ofNullable(found));
        return found;
    }

    @Nullable
    private static Method rawMsGetterForClass(Class<?> c) {
        Optional<Method> cached = RAW_MS_GETTERS.get(c);
        if (cached != null) {
            return cached.orElse(null);
        }
        Method found = findRawMovementStatesGetter(c);
        RAW_MS_GETTERS.put(c, Optional.ofNullable(found));
        return found;
    }

    @Nullable
    private static Field rawMsFieldForClass(Class<?> c) {
        Optional<Field> cached = RAW_MS_FIELDS.get(c);
        if (cached != null) {
            return cached.orElse(null);
        }
        Field found = findRawMovementStatesField(c);
        RAW_MS_FIELDS.put(c, Optional.ofNullable(found));
        return found;
    }

    @Nullable
    private static Field findRawJumpField(Class<?> start) {
        Field best = null;
        int bestScore = -1;
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Class<?> t = f.getType();
                if (t != boolean.class && t != Boolean.class) {
                    continue;
                }
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("jump")) {
                    continue;
                }
                // Prefer more explicit names, but accept any jump boolean if it's all we have.
                int score = 0;
                if (n.contains("pressed") && (n.contains("key") || n.contains("input"))) {
                    score += 5;
                }
                if (n.contains("press") || n.contains("down") || n.contains("held")) {
                    score += 3;
                }
                if (n.contains("jumping")) {
                    score += 2;
                }
                if (n.contains("key") || n.contains("input")) {
                    score += 1;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = f;
                }
            }
        }
        if (best != null) {
            best.setAccessible(true);
        }
        return best;
    }

    @Nullable
    private static Field findRawMovementStatesField(Class<?> start) {
        Field best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!MovementStates.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                String n = f.getName().toLowerCase(Locale.ROOT);
                int score = 0;
                if (n.contains("raw") || n.contains("client") || n.contains("input") || n.contains("pending") || n.contains("desired")) {
                    score += 5;
                }
                if (n.contains("queued") || n.contains("next")) {
                    score += 4;
                }
                if (n.contains("movement")) {
                    score += 2;
                }
                if (n.contains("state")) {
                    score += 1;
                }
                if (n.contains("previous") || (n.contains("last") && n.contains("applied"))) {
                    score -= 3;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = f;
                }
            }
        }
        if (best != null) {
            best.setAccessible(true);
        }
        return best;
    }

    @Nullable
    private static Method findRawMovementStatesGetter(Class<?> start) {
        for (String name : RAW_MS_GETTER_NAMES) {
            try {
                Method m = start.getMethod(name);
                if (m.getParameterCount() == 0 && MovementStates.class.isAssignableFrom(m.getReturnType())) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Class<?> k = start; k != null && k != Object.class; k = k.getSuperclass()) {
            for (String name : RAW_MS_GETTER_NAMES) {
                try {
                    Method m = k.getDeclaredMethod(name);
                    if (m.getParameterCount() == 0 && MovementStates.class.isAssignableFrom(m.getReturnType())) {
                        m.setAccessible(true);
                        return m;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    @Nullable
    private static Method findRawJumpGetter(Class<?> c) {
        // Prefer explicit names first (public, then declared).
        for (String name : RAW_JUMP_METHOD_NAMES) {
            try {
                Method m = c.getMethod(name);
                if (m.getParameterCount() == 0 && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (String name : RAW_JUMP_METHOD_NAMES) {
                try {
                    Method m = k.getDeclaredMethod(name);
                    if (m.getParameterCount() == 0 && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                        m.setAccessible(true);
                        return m;
                    }
                } catch (NoSuchMethodException | SecurityException ignored) {
                }
            }
        }
        // Fallback: any public no-arg boolean method with both "jump" and ("press"/"down"/"held"/"jumping") in its name.
        for (Method m : c.getMethods()) {
            if (m.getParameterCount() != 0) {
                continue;
            }
            Class<?> rt = m.getReturnType();
            if (rt != boolean.class && rt != Boolean.class) {
                continue;
            }
            String n = m.getName().toLowerCase(Locale.ROOT);
            if (!n.contains("jump")) {
                continue;
            }
            if (n.contains("press") || n.contains("down") || n.contains("held") || n.contains("jumping")) {
                return m;
            }
        }
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getParameterCount() != 0) {
                    continue;
                }
                Class<?> rt = m.getReturnType();
                if (rt != boolean.class && rt != Boolean.class) {
                    continue;
                }
                String n = m.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("jump")) {
                    continue;
                }
                if (n.contains("press") || n.contains("down") || n.contains("held") || n.contains("jumping")) {
                    try {
                        m.setAccessible(true);
                    } catch (SecurityException ignored) {
                        continue;
                    }
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * If {@link PlayerInput} exposes a {@link MovementStates} snapshot that disagrees with the applied
     * {@link MovementStatesComponent} value, return that snapshot's {@code jumping}. This often tracks the client's
     * pending key before the merged {@code st.jumping} bit drops between rapid taps (when named raw accessors are absent).
     */
    @Nullable
    private static Boolean divergentJumpSignal(@Nonnull PlayerInput input, @Nonnull MovementStates applied) {
        Class<?> cls = input.getClass();
        for (Method m : allMsGettersForClass(cls)) {
            try {
                Object o = m.invoke(input);
                if (o instanceof MovementStates ms && ms.jumping != applied.jumping) {
                    return ms.jumping;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        for (Field f : allMsFieldsForClass(cls)) {
            try {
                Object o = f.get(input);
                if (o instanceof MovementStates ms && ms.jumping != applied.jumping) {
                    return ms.jumping;
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return null;
    }

    /**
     * Walks the movement queue after {@link PlayerSystems.ProcessPlayerInput} — may still contain SMS, or may differ
     * from the pre-process snapshot {@link DoubleJumpComponent#jumpHeldLastQueue} (scanner runs before processing).
     */
    private static boolean peekLastSmsJumpFromQueue(@Nonnull PlayerInput input, @Nonnull boolean[] hadSmsOut) {
        hadSmsOut[0] = false;
        List<PlayerInput.InputUpdate> q = input.getMovementUpdateQueue();
        if (q == null || q.isEmpty()) {
            return false;
        }
        boolean last = false;
        for (PlayerInput.InputUpdate u : q) {
            Boolean j = jumpBitFromQueueUpdate(u);
            if (j != null) {
                hadSmsOut[0] = true;
                last = j;
            }
        }
        return last;
    }

    private static List<Method> allMsGettersForClass(Class<?> c) {
        return ALL_MS_GETTERS.computeIfAbsent(c, DoubleJumpTicking::collectAllMovementStatesMethods);
    }

    private static List<Field> allMsFieldsForClass(Class<?> c) {
        return ALL_MS_FIELDS_SORTED.computeIfAbsent(c, DoubleJumpTicking::collectAllMovementStatesFields);
    }

    private static List<Method> collectAllMovementStatesMethods(Class<?> start) {
        List<Method> out = new ArrayList<>();
        for (Class<?> k = start; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                if (m.getParameterCount() != 0) {
                    continue;
                }
                if (!MovementStates.class.isAssignableFrom(m.getReturnType())) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                } catch (SecurityException ignored) {
                    continue;
                }
                out.add(m);
            }
        }
        out.sort(Comparator.comparingInt((Method m) -> -movementStatesNameScore(m.getName())));
        return out;
    }

    private static List<Field> collectAllMovementStatesFields(Class<?> start) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = start; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!MovementStates.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                } catch (SecurityException ignored) {
                    continue;
                }
                out.add(f);
            }
        }
        out.sort(Comparator.comparingInt((Field f) -> -movementStatesNameScore(f.getName())));
        return out;
    }

    private static int movementStatesNameScore(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int score = 0;
        if (n.contains("raw") || n.contains("client") || n.contains("input") || n.contains("pending") || n.contains("desired")) {
            score += 5;
        }
        if (n.contains("queued") || n.contains("next")) {
            score += 4;
        }
        if (n.contains("movement")) {
            score += 2;
        }
        if (n.contains("state")) {
            score += 1;
        }
        if (n.contains("previous") || (n.contains("last") && n.contains("applied"))) {
            score -= 3;
        }
        return score;
    }

    static boolean tryApply(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull DoubleJumpComponent dj,
        @Nonnull DoubleJumpConfig config
    ) {
        return tryApplyResult(ref, commandBuffer, dj, config) == ApplyResult.APPLIED;
    }

    static ApplyResult tryApplyResult(
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
            return ApplyResult.REJECT_NO_MOVEMENT_COMPONENT;
        }
        if (!airborne(move.getMovementStates())) {
            return ApplyResult.REJECT_NOT_AIRBORNE;
        }
        if (!config.infiniteDoubleJump && dj.chargesRemaining <= 0) {
            return ApplyResult.REJECT_NO_CHARGES;
        }
        if (config.cooldownMs > 0L && nowMs - dj.lastDoubleJumpTimeMs < config.cooldownMs) {
            return ApplyResult.REJECT_COOLDOWN;
        }

        EntityStatMap stats = (EntityStatMap) commandBuffer.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue stamina = stats != null ? stats.get(StatUtil.staminaIndex()) : null;
        float staminaCost = stamina == null ? 0f : staminaCost(config, stamina);
        if (staminaCost > 0f && stamina.get() < staminaCost) {
            return ApplyResult.REJECT_STAMINA;
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
        return ApplyResult.APPLIED;
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
        dj.movementQueueHadSms = false;
        dj.postLiftoffSignalMaskTicksRemaining = 0;
        dj.inputState = DoubleJumpComponent.InputState.WAITING_FOR_PRESS;
        dj.rawSignalLast = false;
        dj.ticksWaitingForSecondJump = 0;
        dj.sawSignalLowWhileWaiting = false;
        dj.tapAssistConsumedThisAirborne = false;
        dj.secondJumpSyntheticConsumedThisAirborne = false;
        dj.secondPressGraceTicksRemaining = 0;
        dj.totalQueueUpdatesThisTick = 0;
        dj.nonSmsQueueUpdatesThisTick = 0;
        dj.inputCooldownFramesRemaining = 0;
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
     * Runs before {@link PlayerSystems.ProcessPlayerInput}: jump rising edges from {@link PlayerInput#getMovementUpdateQueue()}
     * using only {@link PlayerInput.SetMovementStates} entries.
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
            List<PlayerInput.InputUpdate> queue = input.getMovementUpdateQueue();
            int[] queueAct = new int[2];
            PlayerInputQueueMixin.takeQueueActivitySnapshot(input, queueAct);
            // When Hyxin mixin is absent or queue() is not hit the same way, snapshot stays 0 while the buffer still
            // has updates (see traces: movementQueue size>0 but qTot=0). Synthetic second-jump and tuning need counts.
            if (queueAct[0] == 0 && queue != null && !queue.isEmpty()) {
                int tot = 0;
                int nonSms = 0;
                for (PlayerInput.InputUpdate u : queue) {
                    tot++;
                    if (!(u instanceof PlayerInput.SetMovementStates)) {
                        nonSms++;
                    }
                }
                queueAct[0] = tot;
                queueAct[1] = nonSms;
            }
            dj.totalQueueUpdatesThisTick = queueAct[0];
            dj.nonSmsQueueUpdatesThisTick = queueAct[1];
            dj.pendingQueueJumpEdge = false;
            dj.movementQueueHadSms = false;
            if (queue == null || queue.isEmpty()) {
                // No SMS walk this tick — avoid leaving jumpHeldLastQueue stuck true from an older SMS batch (blocks
                // synthetic second-jump when the queue is only velocity/body/head updates).
                dj.jumpHeldLastQueue = msc.getMovementStates().jumping;
                return;
            }

            long nowMs =
                ((TimeResource) cmd.getResource(TimeResource.getResourceType())).getNow().toEpochMilli();
            long edgeBufMs = cfg.queueJumpEdgeBufferMs > 0 ? cfg.queueJumpEdgeBufferMs : 120L;

            MovementStates startMs = msc.getMovementStates();
            boolean serverAirborne = airborne(startMs);

            boolean prevQJump = dj.jumpHeldLastQueue;
            boolean curQJump = prevQJump;
            boolean risingEdge = false;
            for (PlayerInput.InputUpdate update : queue) {
                Boolean j = jumpBitFromQueueUpdate(update);
                if (j == null) {
                    continue;
                }
                dj.movementQueueHadSms = true;
                if (j && !curQJump) {
                    risingEdge = true;
                }
                curQJump = j;
            }
            if (!dj.movementQueueHadSms) {
                dj.jumpHeldLastQueue = startMs.jumping;
            } else {
                dj.jumpHeldLastQueue = curQJump;
            }

            if (!serverAirborne) {
                return;
            }

            if (!cfg.infiniteDoubleJump && (dj.chargesRemaining <= 0 || dj.phase == DoubleJumpComponent.Phase.AIR_SPENT)) {
                return;
            }

            boolean netEdge = curQJump && !prevQJump;
            if (risingEdge || netEdge) {
                dj.pendingQueueJumpEdge = true;
                dj.queueJumpEdgeBufferUntilMs = nowMs + edgeBufMs;
            }
        }
    }

    /**
     * After {@link PlayerSystems.ProcessPlayerInput}: phase transitions and jump-key trigger from processed movement
     * and/or {@link DoubleJumpComponent#pendingQueueJumpEdge}. Extra jumps do not require a mid-air key release — charges
     * and liftoff exclusion prevent spam; release-then-press was blocking held-jump liftoffs.
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
            return Query.and(djType, PlayerInput.getComponentType());
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
            PlayerInput input = chunk.getComponent(index, PlayerInput.getComponentType());
            if (dj == null || cfg == null || msc == null || input == null) {
                return;
            }

            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            long nowMsBridge =
                ((TimeResource) cmd.getResource(TimeResource.getResourceType())).getNow().toEpochMilli();
            JossDoubleJumpTraceBridge.beginTick(ref, cmd, input, nowMsBridge);
            MovementStates st = msc.getMovementStates();
            boolean grounded = st.onGround || st.inFluid || st.climbing;

            if (grounded) {
                onGrounded(dj, cfg);
                return;
            }

            DoubleJumpComponent.InputState inputStateAtTickStart = dj.inputState;

            boolean liftoffTick = dj.phase == DoubleJumpComponent.Phase.GROUNDED;
            if (!cfg.infiniteDoubleJump && liftoffTick) {
                dj.chargesRemaining = Math.max(0, dj.chargesRemaining - 1);
            }

            tickAirborne(dj, cfg);

            if (liftoffTick) {
                int maskTicks = Math.max(0, cfg.postLiftoffJumpSignalIgnoreTicks);
                dj.postLiftoffSignalMaskTicksRemaining = maskTicks;
                // First-jump SMS edge + buffer must not satisfy queueEdge on the next tick (tryApply skipped this tick).
                dj.queueJumpEdgeBufferUntilMs = 0L;
                dj.pendingQueueJumpEdge = false;
            }

            long nowMs =
                ((TimeResource) cmd.getResource(TimeResource.getResourceType())).getNow().toEpochMilli();
            boolean mixinSmsEdge = PlayerInputQueueMixin.consumeJumpRisingEdge(input);
            boolean queueEdge =
                mixinSmsEdge
                    || dj.pendingQueueJumpEdge
                    || (dj.queueJumpEdgeBufferUntilMs != 0L && nowMs < dj.queueJumpEdgeBufferUntilMs);
            int debounceFrames = Math.max(1, cfg.inputDebounceFrames);
            // Signal order: raw; divergent pending MS vs applied; live SMS walk of queue after ProcessPlayerInput
            // (queueSmsLive); pre-process QueueScanner SMS end-state (queueSms); processed st.jumping (fallback).
            // Post-liftoff mask blanks effectiveSignal for fallback + both queue paths so held jump can register as a
            // new press (see DoubleJumpConfig.postLiftoffJumpSignalIgnoreTicks). Hyxin mixin still supplies rising-edge
            // consumption only (consumeJumpRisingEdge).
            Boolean rawJump = rawJumpPressedFromInput(input);
            Boolean divergent = divergentJumpSignal(input, st);
            boolean[] liveHadSms = new boolean[1];
            boolean liveEndJump = peekLastSmsJumpFromQueue(input, liveHadSms);
            boolean signal;
            String src;
            if (rawJump != null && rawJump.booleanValue() == st.jumping && liveHadSms[0]) {
                // Raw accessor matched merged movement only — no client lead. Prefer last SMS in the queue so a
                // second tap can still produce an unmasked edge when stationary input is sparse (see queueSmsLive).
                signal = liveEndJump;
                src = "queueSmsLive";
            } else if (rawJump != null) {
                signal = rawJump;
                src = "raw";
            } else if (divergent != null) {
                signal = divergent;
                src = "pendingMs";
            } else if (liveHadSms[0]) {
                signal = liveEndJump;
                src = "queueSmsLive";
            } else if (dj.movementQueueHadSms) {
                signal = dj.jumpHeldLastQueue;
                src = "queueSms";
            } else {
                signal = st.jumping;
                src = "fallback";
            }
            boolean postLiftoffMask =
                dj.postLiftoffSignalMaskTicksRemaining > 0
                    && ("fallback".equals(src) || "queueSms".equals(src) || "queueSmsLive".equals(src));
            boolean effectiveSignal = postLiftoffMask ? false : signal;
            // Rising edge uses **unmasked** signal vs previous unmasked sample — never effectiveSignal, or the post-liftoff
            // mask would make rawSignalLast track the masked path and break edge semantics.
            boolean edge = signal && !dj.rawSignalLast;
            boolean sourceConflict =
                divergent != null || (rawJump != null && rawJump.booleanValue() != st.jumping);
            long nowNano = System.nanoTime();
            JossDoubleJumpTraceBridge.afterInference(
                ref,
                cmd,
                input,
                st,
                signal,
                src,
                edge,
                postLiftoffMask,
                sourceConflict,
                nowNano);

            boolean canDoubleThisAir =
                dj.phase == DoubleJumpComponent.Phase.AIR_CAN_DOUBLE
                    && (cfg.infiniteDoubleJump || dj.chargesRemaining > 0);
            if (!liftoffTick && canDoubleThisAir && inputStateAtTickStart == DoubleJumpComponent.InputState.WAITING_FOR_PRESS) {
                dj.ticksWaitingForSecondJump++;
                if (!signal) {
                    dj.sawSignalLowWhileWaiting = true;
                }
            } else if (!liftoffTick) {
                dj.ticksWaitingForSecondJump = 0;
                if (inputStateAtTickStart == DoubleJumpComponent.InputState.HELD) {
                    dj.sawSignalLowWhileWaiting = false;
                }
            }

            boolean tapAssist =
                cfg.tapAssistMinWaitingTicks > 0
                    && !liftoffTick
                    && dj.phase == DoubleJumpComponent.Phase.AIR_CAN_DOUBLE
                    && (cfg.infiniteDoubleJump || dj.chargesRemaining > 0)
                    && inputStateAtTickStart == DoubleJumpComponent.InputState.WAITING_FOR_PRESS
                    && signal
                    && dj.sawSignalLowWhileWaiting
                    && dj.ticksWaitingForSecondJump >= cfg.tapAssistMinWaitingTicks
                    && !dj.tapAssistConsumedThisAirborne
                    && !edge;

            // Input FSM transitions.
            if (dj.inputState == DoubleJumpComponent.InputState.COOLDOWN_FRAMES) {
                if (dj.inputCooldownFramesRemaining > 0) {
                    dj.inputCooldownFramesRemaining--;
                }
                if (dj.inputCooldownFramesRemaining <= 0) {
                    dj.inputState =
                        effectiveSignal ? DoubleJumpComponent.InputState.HELD : DoubleJumpComponent.InputState.WAITING_FOR_PRESS;
                }
            } else if (effectiveSignal) {
                dj.inputState = DoubleJumpComponent.InputState.HELD;
            } else {
                dj.inputState = DoubleJumpComponent.InputState.WAITING_FOR_PRESS;
            }

            boolean requestFromEdge =
                edge
                    && (!cfg.requireReleaseForDoubleJump
                        || inputStateAtTickStart == DoubleJumpComponent.InputState.WAITING_FOR_PRESS);
            boolean requestSecondJump = requestFromEdge || tapAssist;
            boolean releaseEdge = !signal && dj.rawSignalLast;
            JossDoubleJumpTraceBridge.beforeDecision(ref, cmd, input, requestSecondJump, releaseEdge);

            if (dj.postLiftoffSignalMaskTicksRemaining > 0) {
                dj.postLiftoffSignalMaskTicksRemaining--;
            }

            ApplyResult applyTrace = ApplyResult.REJECT_NOT_REQUESTED;
            if (DoubleJumpConfig.ActivationMode.from(cfg) == DoubleJumpConfig.ActivationMode.JUMP_KEY
                && dj.phase == DoubleJumpComponent.Phase.AIR_CAN_DOUBLE
                && !liftoffTick
                && (queueEdge || requestSecondJump)
                && (cfg.infiniteDoubleJump || dj.chargesRemaining > 0)) {
                applyTrace = tryApplyResult(ref, cmd, dj, cfg);
                if (applyTrace == ApplyResult.APPLIED) {
                    dj.queueJumpEdgeBufferUntilMs = 0L;
                    if (tapAssist) {
                        dj.tapAssistConsumedThisAirborne = true;
                    }
                    // Debounce only after a real apply — pre-apply cooldown locked WAITING during rejects (stamina,
                    // cooldownMs, etc.) and broke requireReleaseForDoubleJump + tapAssist until debounce expired.
                    dj.inputState = DoubleJumpComponent.InputState.COOLDOWN_FRAMES;
                    dj.inputCooldownFramesRemaining = debounceFrames;
                }
            }
            JossDoubleJumpTraceBridge.afterDecisionApply(ref, cmd, input, dj, applyTrace);

            // Optional: slight initial jump boost (ground liftoff) for feel.
            if (liftoffTick && cfg.initialJumpBoostY > 0f && signal) {
                Velocity v = (Velocity) cmd.getComponent(ref, Velocity.getComponentType());
                if (v != null) {
                    v.addInstruction(new Vector3d(0.0, cfg.initialJumpBoostY, 0.0), null, ChangeVelocityType.Add);
                }
            }

            dj.pendingQueueJumpEdge = false;
            dj.rawSignalLast = signal;
            // jumpHeldLastQueue: QueueScanner sets it from SMS walk when present; when the queue has no SMS it mirrors
            // applied movementStates.jumping so stale SMS does not block second-jump edge/tap heuristics.
        }
    }
}
