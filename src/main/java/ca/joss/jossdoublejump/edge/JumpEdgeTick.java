package ca.joss.jossdoublejump.edge;

import ca.joss.jossdoublejump.DoubleJumpConfig;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

/** Drain / clear helpers for {@link ca.joss.jossdoublejump.DoubleJumpTicking.AfterInputSystem}. */
public final class JumpEdgeTick {

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

    /**
     * Whether jump-key mode should use the edge channel for raw signal this tick (fresh, enabled, and at least one edge
     * packet ever received).
     */
    public static boolean shouldUseJumpEdgeChannel(Ref<EntityStore> ref, DoubleJumpConfig cfg, boolean jumpKeyMode) {
        if (cfg == null || !jumpKeyMode || !cfg.useJumpEdgeChannel) {
            return false;
        }
        if (!cfg.fallbackToSmsHeld) {
            // Reserved: still require freshness + packets; strict mode can be extended later.
        }
        if (!JumpKeyAuthority.hasEverReceivedEdge(ref)) {
            return false;
        }
        if (!JumpKeyAuthority.isFresh(ref, cfg.edgeChannelTimeoutMs)) {
            return false;
        }
        return true;
    }
}
