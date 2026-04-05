package ca.joss.jossdoublejump.edge;

import ca.joss.jossdoublejump.DoubleJumpConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

/**
 * Thread-safe queue of {@link JumpEdgeMessage} per player. Ingest from the network thread; drain on the world tick
 * before jump signal selection.
 */
public final class JumpEdgeIngress {

    private static final HytaleLogger LOG = Log.INSTANCE;
    private static final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<JumpEdgeMessage>> QUEUES = new ConcurrentHashMap<>();
    private static final AtomicBoolean PROOF_ONCE = new AtomicBoolean();

    private JumpEdgeIngress() {}

    /** Raw wire string (may include {@link JumpEdgeMessage#PREFIX}); called from mixin. */
    public static void onWirePayload(PlayerRef playerRef, String data) {
        if (playerRef == null || data == null || !data.startsWith(JumpEdgeMessage.PREFIX)) {
            return;
        }
        String json = data.substring(JumpEdgeMessage.PREFIX.length());
        JumpEdgeMessage m = JumpEdgeMessage.tryParseJsonBody(json);
        if (m == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        QUEUES.computeIfAbsent(uuid, u -> new ConcurrentLinkedQueue<>()).offer(m);
    }

    /**
     * Drain all queued messages for this player into {@link JumpKeyAuthority} (respecting rate limit per message).
     */
    public static void drainForPlayer(PlayerRef playerRef, DoubleJumpConfig cfg) {
        if (playerRef == null || cfg == null) {
            return;
        }
        ConcurrentLinkedQueue<JumpEdgeMessage> q = QUEUES.get(playerRef.getUuid());
        if (q == null || q.isEmpty()) {
            return;
        }
        JumpEdgeMessage m;
        while ((m = q.poll()) != null) {
            boolean accepted = JumpKeyAuthority.tryAcceptWithRateLimit(playerRef, m, cfg);
            if (accepted && PROOF_ONCE.compareAndSet(false, true)) {
                ((HytaleLogger.Api) LOG.atInfo())
                    .log(
                        "[JDJ edge] transport proof: first jump-edge packet accepted (seq=%d t=%s user=%s)",
                        m.seq,
                        m.t,
                        playerRef.getUsername());
            }
        }
    }

    /** Inject a message for testing (same path as network). */
    public static void injectForTesting(PlayerRef playerRef, JumpEdgeMessage m) {
        if (playerRef == null || m == null) {
            return;
        }
        QUEUES.computeIfAbsent(playerRef.getUuid(), u -> new ConcurrentLinkedQueue<>()).offer(m);
    }

    private static final class Log {
        static final HytaleLogger INSTANCE = HytaleLogger.get("DoubleJump");

        private Log() {}
    }
}
