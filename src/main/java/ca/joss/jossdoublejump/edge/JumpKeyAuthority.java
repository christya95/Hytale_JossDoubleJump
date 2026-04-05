package ca.joss.jossdoublejump.edge;

import ca.joss.jossdoublejump.DoubleJumpConfig;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Per-player authoritative jump state from the edge channel. Tick-scoped DOWN/UP flags are cleared each
 * {@link JumpEdgeTick#endAfterInputTick}. {@link #pendingAuthoritativeDownEdge} survives across ticks until consumed by
 * the double-jump latch, cleared on UP, landing, or successful apply.
 */
public final class JumpKeyAuthority {

    private static final HytaleLogger LOG = Log.INSTANCE;

    private static final class State {
        long lastAcceptedSeq = Long.MIN_VALUE;
        boolean jumpHeld;
        boolean jumpDownThisTick;
        boolean jumpUpThisTick;
        long lastEdgeArrivalNanos;
        long rateWindowStartNs;
        int rateCountInWindow;
        /** One-shot carry: DOWN accepted but not yet consumed by second-jump latch / tryApply. */
        boolean pendingAuthoritativeDownEdge;
    }

    private static final ConcurrentHashMap<UUID, State> BY_UUID = new ConcurrentHashMap<>();

    private JumpKeyAuthority() {}

    private static State state(UUID uuid) {
        return BY_UUID.computeIfAbsent(uuid, u -> new State());
    }

    public static void removePlayer(UUID uuid) {
        BY_UUID.remove(uuid);
    }

    public static boolean hasEverReceivedEdge(Ref<EntityStore> ref) {
        PlayerRef pr = playerRef(ref);
        if (pr == null) {
            return false;
        }
        State s = BY_UUID.get(pr.getUuid());
        return s != null && s.lastEdgeArrivalNanos != 0L;
    }

    public static boolean isFresh(Ref<EntityStore> ref, long edgeChannelTimeoutMs) {
        PlayerRef pr = playerRef(ref);
        if (pr == null) {
            return false;
        }
        State s = BY_UUID.get(pr.getUuid());
        if (s == null || s.lastEdgeArrivalNanos == 0L) {
            return false;
        }
        long maxNanos = Math.max(1L, edgeChannelTimeoutMs) * 1_000_000L;
        return System.nanoTime() - s.lastEdgeArrivalNanos <= maxNanos;
    }

    /** Age of last edge packet in ms, or -1 if none. */
    public static long lastEdgeAgeMs(Ref<EntityStore> ref) {
        PlayerRef pr = playerRef(ref);
        if (pr == null) {
            return -1L;
        }
        State s = BY_UUID.get(pr.getUuid());
        if (s == null || s.lastEdgeArrivalNanos == 0L) {
            return -1L;
        }
        return (System.nanoTime() - s.lastEdgeArrivalNanos) / 1_000_000L;
    }

    public static boolean isJumpHeld(Ref<EntityStore> ref) {
        PlayerRef pr = playerRef(ref);
        if (pr == null) {
            return false;
        }
        State s = BY_UUID.get(pr.getUuid());
        return s != null && s.jumpHeld;
    }

    public static boolean isJumpDownThisTick(Ref<EntityStore> ref) {
        PlayerRef pr = playerRef(ref);
        if (pr == null) {
            return false;
        }
        State s = BY_UUID.get(pr.getUuid());
        return s != null && s.jumpDownThisTick;
    }

    public static boolean isJumpUpThisTick(Ref<EntityStore> ref) {
        PlayerRef pr = playerRef(ref);
        if (pr == null) {
            return false;
        }
        State s = BY_UUID.get(pr.getUuid());
        return s != null && s.jumpUpThisTick;
    }

    public static boolean hasPendingAuthoritativeDownEdge(Ref<EntityStore> ref) {
        PlayerRef pr = playerRef(ref);
        if (pr == null) {
            return false;
        }
        State s = BY_UUID.get(pr.getUuid());
        return s != null && s.pendingAuthoritativeDownEdge;
    }

    /**
     * Clears pending DOWN carry (landing, explicit clear). Does not touch held/tick flags.
     */
    public static void clearPendingOnLand(Ref<EntityStore> ref) {
        PlayerRef pr = playerRef(ref);
        if (pr == null) {
            return;
        }
        State st = BY_UUID.get(pr.getUuid());
        if (st == null) {
            return;
        }
        synchronized (st) {
            st.pendingAuthoritativeDownEdge = false;
        }
    }

    /**
     * Clears pending after second-jump tryApply succeeded.
     */
    public static void clearPendingAuthoritativeDownEdge(Ref<EntityStore> ref) {
        clearPendingOnLand(ref);
    }

    /**
     * Rate-limited accept; ignores stale {@code seq}. Logs diagnostics when configured.
     *
     * @return true if accepted
     */
    public static boolean tryAcceptWithRateLimit(PlayerRef playerRef, JumpEdgeMessage m, DoubleJumpConfig cfg) {
        if (playerRef == null || m == null) {
            return false;
        }
        State st = state(playerRef.getUuid());
        int cap = cfg != null ? Math.max(1, cfg.edgePacketsPerSecondLimit) : 30;
        synchronized (st) {
            long now = System.nanoTime();
            if (st.rateWindowStartNs == 0L || now - st.rateWindowStartNs > 1_000_000_000L) {
                st.rateWindowStartNs = now;
                st.rateCountInWindow = 0;
            }
            if (st.rateCountInWindow >= cap) {
                return false;
            }
            if (m.seq <= st.lastAcceptedSeq) {
                return false;
            }
            st.rateCountInWindow++;
            boolean heldBefore = st.jumpHeld;
            boolean pendingBefore = st.pendingAuthoritativeDownEdge;
            st.lastAcceptedSeq = m.seq;
            st.lastEdgeArrivalNanos = now;
            if ("DOWN".equals(m.t)) {
                st.jumpHeld = true;
                st.jumpDownThisTick = true;
                st.pendingAuthoritativeDownEdge = true;
            } else {
                st.jumpHeld = false;
                st.jumpUpThisTick = true;
                st.pendingAuthoritativeDownEdge = false;
            }
            boolean heldAfter = st.jumpHeld;
            boolean pendingAfter = st.pendingAuthoritativeDownEdge;
            if (cfg != null && cfg.traceJumpAuthorityDiagnostics) {
                ((HytaleLogger.Api) LOG.atInfo())
                    .log(
                        "[JDJ edge] accepted seq=%d type=%s user=%s heldBefore=%b heldAfter=%b pendingBefore=%b pendingAfter=%b",
                        m.seq,
                        m.t,
                        playerRef.getUsername(),
                        heldBefore,
                        heldAfter,
                        pendingBefore,
                        pendingAfter);
            }
            return true;
        }
    }

    /** Clears tick-scoped DOWN/UP flags after {@link ca.joss.jossdoublejump.DoubleJumpTicking.AfterInputSystem} tick. */
    public static void clearTickEdgeFlags(Ref<EntityStore> ref) {
        PlayerRef pr = playerRef(ref);
        if (pr == null) {
            return;
        }
        State st = BY_UUID.get(pr.getUuid());
        if (st == null) {
            return;
        }
        synchronized (st) {
            st.jumpDownThisTick = false;
            st.jumpUpThisTick = false;
        }
    }

    @Nullable
    private static PlayerRef playerRef(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Player p = (Player) ref.getStore().getComponent(ref, Player.getComponentType());
        return p != null ? p.getPlayerRef() : null;
    }

    private static final class Log {
        static final HytaleLogger INSTANCE = HytaleLogger.get("DoubleJump");

        private Log() {}
    }
}
