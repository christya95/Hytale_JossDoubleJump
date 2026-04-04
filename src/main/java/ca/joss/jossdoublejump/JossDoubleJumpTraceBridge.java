package ca.joss.jossdoublejump;

import amore.servercomm.api.ApplyResult;
import amore.servercomm.capture.MovementBits;
import amore.servercomm.dump.TraceDumpService;
import amore.servercomm.incident.IncidentDetector;
import amore.servercomm.registry.PerPlayerTraceState;
import amore.servercomm.registry.ServerCommRegistry;
import amore.servercomm.tick.TraceRecord;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nullable;

/**
 * JossDoubleJump adapter: fills {@link TraceRecord} from mod FSM + queue, drives ring commit and incidents. Tracing
 * only; does not change gameplay.
 */
public final class JossDoubleJumpTraceBridge {

    private static final HytaleLogger LOG = HytaleLogger.get("DoubleJump");

    private JossDoubleJumpTraceBridge() {}

    public static void beginTick(
        @Nullable Ref<EntityStore> ref,
        @Nullable CommandBuffer<EntityStore> cmd,
        @Nullable PlayerInput input,
        long nowMs
    ) {
        if (ref == null || cmd == null || input == null || !ref.isValid()) {
            return;
        }
        PlayerRef pr = (PlayerRef) cmd.getComponent(ref, PlayerRef.getComponentType());
        String user = pr != null ? pr.getUsername() : null;
        if (user == null || !amore.servercomm.api.ServerComm.isTracing(user)) {
            return;
        }
        PerPlayerTraceState st = ServerCommRegistry.stateFor(input);
        st.scratch.clear();
        st.scratch.tickId = nowMs;
        st.scratch.serverTimeNs = System.nanoTime();
        st.mailbox.copyTo(st.mailboxSnap);
        st.scratch.rxSmsNs = st.mailboxSnap.smsNs;
        st.scratch.rxCmNs = st.mailboxSnap.cmNs;
        st.scratch.rxIcNs = st.mailboxSnap.icNs;
        st.scratch.rxMiNs = st.mailboxSnap.miNs;
        st.scratch.seqSms = st.mailboxSnap.smsSeq;
        st.scratch.seqCm = st.mailboxSnap.cmSeq;
        st.scratch.seqIc = st.mailboxSnap.icSeq;
        st.scratch.seqMi = st.mailboxSnap.miSeq;
    }

    public static void afterInference(
        @Nullable Ref<EntityStore> ref,
        @Nullable CommandBuffer<EntityStore> cmd,
        @Nullable PlayerInput input,
        MovementStates st,
        boolean signal,
        String src,
        boolean edge,
        boolean postLiftoffMask,
        boolean sourceConflict,
        long nowNano
    ) {
        if (!tracing(ref, cmd, input)) {
            return;
        }
        PerPlayerTraceState s = ServerCommRegistry.stateFor(input);
        long gap = nowNano - s.lastIngressNano;
        boolean starve = s.lastIngressNano != 0L && gap > IncidentDetector.DEFAULT_STARVATION_NS;

        s.scratch.movementBits = MovementBits.pack(st);
        s.scratch.jumpHeld = signal;
        s.scratch.jumpEdge = edge;
        s.scratch.onGround = st.onGround || st.inFluid || st.climbing;
        s.scratch.postLiftoffMaskActive = postLiftoffMask;
        s.scratch.sourceConflict = sourceConflict;
        s.scratch.starvation = starve;
        s.scratch.sourceTag = encodeSource(src);
        s.scratch.wishMoveQ = s.mailboxSnap.cmWishQ;
        s.scratch.velYQ = s.mailboxSnap.cmVelYQ;
    }

    public static void beforeDecision(
        @Nullable Ref<EntityStore> ref,
        @Nullable CommandBuffer<EntityStore> cmd,
        @Nullable PlayerInput input,
        boolean requestSecondJump,
        boolean releaseEdge
    ) {
        if (!tracing(ref, cmd, input)) {
            return;
        }
        PerPlayerTraceState s = ServerCommRegistry.stateFor(input);
        List<PlayerInput.InputUpdate> q = input != null ? input.getMovementUpdateQueue() : null;
        s.scratch.queueSize = q != null ? q.size() : 0;
        s.scratch.queueHeadAgeTicks = Math.min(255, s.scratch.queueSize);
        s.scratch.reasonCode = requestSecondJump ? 1 : 0;
        s.scratch.releaseEdge = releaseEdge;
    }

    public static void afterDecisionApply(
        @Nullable Ref<EntityStore> ref,
        @Nullable CommandBuffer<EntityStore> cmd,
        @Nullable PlayerInput input,
        DoubleJumpComponent dj,
        ApplyResult applyResult
    ) {
        if (!tracing(ref, cmd, input)) {
            return;
        }
        PerPlayerTraceState s = ServerCommRegistry.stateFor(input);
        s.scratch.applyResult = applyResult;
        s.scratch.fsmState = encodeFsm(dj);
        s.scratch.extraJumpCount = dj.jumpCount;
        long nowMs = System.currentTimeMillis();
        s.scratch.extraCooldownMsRemaining = Math.max(0L, (dj.lastDoubleJumpTimeMs + 500) - nowMs);
        s.scratch.extraLiftoffDelta = 0L;

        TraceRecord slot = s.ring.nextWritable();
        slot.copyFrom(s.scratch);

        PlayerRef pr = (PlayerRef) cmd.getComponent(ref, PlayerRef.getComponentType());
        String user = pr != null ? pr.getUsername() : "?";
        IncidentDetector.maybeReport(
            LOG,
            user,
            s.scratch,
            () -> {
                try {
                    TraceDumpService.dumpNdjson(s, user, 15);
                } catch (Exception e) {
                    ((HytaleLogger.Api) LOG.atWarning()).log("Amore auto-dump failed: " + e.getMessage());
                }
            });
    }

    private static boolean tracing(Ref<EntityStore> ref, CommandBuffer<EntityStore> cmd, PlayerInput input) {
        if (ref == null || cmd == null || input == null || !ref.isValid()) {
            return false;
        }
        PlayerRef pr = (PlayerRef) cmd.getComponent(ref, PlayerRef.getComponentType());
        String user = pr != null ? pr.getUsername() : null;
        return user != null && amore.servercomm.api.ServerComm.isTracing(user);
    }

    private static byte encodeSource(String src) {
        if (src == null) {
            return 0;
        }
        return switch (src) {
            case "raw" -> 1;
            case "pendingMs" -> 2;
            case "queueSmsLive" -> 3;
            case "queueSms" -> 4;
            case "fallback" -> 5;
            default -> 0;
        };
    }

    private static byte encodeFsm(DoubleJumpComponent dj) {
        int p = dj.phase.ordinal();
        int i = dj.inputState.ordinal();
        return (byte) ((p << 4) | (i & 0xf));
    }
}
