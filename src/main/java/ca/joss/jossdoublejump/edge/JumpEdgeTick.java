package ca.joss.jossdoublejump.edge;

import ca.joss.jossdoublejump.DoubleJumpConfig;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

/** Drain / clear helpers for {@link ca.joss.jossdoublejump.DoubleJumpTicking.AfterInputSystem}. */
public final class JumpEdgeTick {

    private static final HytaleLogger LOG = Log.INSTANCE;

    private JumpEdgeTick() {}

    @Nullable
    private static PlayerRef playerRef(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Player p = (Player) ref.getStore().getComponent(ref, Player.getComponentType());
        return p != null ? p.getPlayerRef() : null;
    }

    /** Call at the start of {@link ca.joss.jossdoublejump.DoubleJumpTicking.AfterInputSystem#tick} (before grounded check). */
    public static void drainAtTickStart(Ref<EntityStore> ref, DoubleJumpConfig cfg) {
        PlayerRef pr = playerRef(ref);
        if (pr == null || cfg == null) {
            return;
        }
        JumpEdgeIngress.drainForPlayer(pr, cfg);
    }

    /** Call at the end of {@link ca.joss.jossdoublejump.DoubleJumpTicking.AfterInputSystem#tick} (finally + grounded early return). */
    public static void endAfterInputTick(Ref<EntityStore> ref) {
        JumpKeyAuthority.clearTickEdgeFlags(ref);
    }

    /** Trust {@link JumpKeyAuthority#isJumpHeld} when the last edge packet is within {@link DoubleJumpConfig#edgeChannelTimeoutMs}. */
    public static boolean edgeHeldHealthy(Ref<EntityStore> ref, DoubleJumpConfig cfg) {
        if (cfg == null || !cfg.useJumpEdgeChannel) {
            return false;
        }
        return JumpKeyAuthority.hasEverReceivedEdge(ref) && JumpKeyAuthority.isFresh(ref, cfg.edgeChannelTimeoutMs);
    }

    /**
     * True if this tick has a DOWN edge or an unconsumed pending authoritative DOWN from a prior tick.
     */
    public static boolean edgePressActive(Ref<EntityStore> ref, DoubleJumpConfig cfg) {
        if (cfg == null || !cfg.useJumpEdgeChannel) {
            return false;
        }
        return JumpKeyAuthority.isJumpDownThisTick(ref) || JumpKeyAuthority.hasPendingAuthoritativeDownEdge(ref);
    }

    /**
     * Use edge-channel semantics for press edge / mask / pressEdgeThisTick grouping when held is healthy OR a press
     * (tick DOWN or pending carry) exists.
     */
    public static boolean edgeChannelInPlay(Ref<EntityStore> ref, DoubleJumpConfig cfg, boolean jumpKeyMode) {
        return cfg != null
            && jumpKeyMode
            && cfg.useJumpEdgeChannel
            && (edgeHeldHealthy(ref, cfg) || edgePressActive(ref, cfg));
    }

    /**
     * @deprecated Prefer {@link #edgeChannelInPlay} / {@link #edgeHeldHealthy} / {@link #edgePressActive}.
     */
    @Deprecated
    public static boolean shouldUseJumpEdgeChannel(Ref<EntityStore> ref, DoubleJumpConfig cfg, boolean jumpKeyMode) {
        return edgeChannelInPlay(ref, cfg, jumpKeyMode);
    }

    public static void logEdgeSelection(
        Ref<EntityStore> ref,
        DoubleJumpConfig cfg,
        boolean jumpKeyMode,
        String latchUser,
        boolean edgeChannelInPlay,
        boolean edgeHeldHealthy,
        boolean edgePressActive
    ) {
        if (cfg == null || !cfg.traceJumpAuthorityDiagnostics || !jumpKeyMode || !cfg.useJumpEdgeChannel) {
            return;
        }
        String reasonFalse = "";
        if (!edgeChannelInPlay) {
            if (!JumpKeyAuthority.hasEverReceivedEdge(ref)) {
                reasonFalse = "no edge packets yet";
            } else if (!edgeHeldHealthy && !edgePressActive) {
                reasonFalse = "no active press or fresh held";
            } else {
                reasonFalse = "edge unavailable";
            }
        }
        ((HytaleLogger.Api) LOG.atInfo())
            .log(
                "[JDJ edge] selection edgeChannel=%b reasonIfFalse=%s hasPending=%b jumpDownThisTick=%b jumpHeld=%b lastEdgeAgeMs=%d user=%s",
                edgeChannelInPlay,
                reasonFalse,
                JumpKeyAuthority.hasPendingAuthoritativeDownEdge(ref),
                JumpKeyAuthority.isJumpDownThisTick(ref),
                JumpKeyAuthority.isJumpHeld(ref),
                JumpKeyAuthority.lastEdgeAgeMs(ref),
                latchUser);
    }

    private static final class Log {
        static final HytaleLogger INSTANCE = HytaleLogger.get("DoubleJump");

        private Log() {}
    }
}
