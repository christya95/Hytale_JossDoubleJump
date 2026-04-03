package ca.joss.jossdoublejump.input;

import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-{@link PlayerInput} state updated from a Hyxin mixin that hooks {@link PlayerInput#queue} at
 * {@code HEAD}. Tracks the last {@link com.hypixel.hytale.protocol.MovementStates#jumping} bit seen from
 * {@link PlayerInput.SetMovementStates} packets and whether a rising edge occurred since the last consume.
 */
public final class JossQueueJumpTracker {

    private static final class S {
        boolean lastSmsJump;
        boolean risingPending;
    }

    private static final Map<PlayerInput, S> BY_INPUT = Collections.synchronizedMap(new WeakHashMap<>());

    private JossQueueJumpTracker() {}

    /**
     * Called from {@code PlayerInputQueueMixin} when Hyxin applies; no-op if mixin is absent.
     */
    public static void onMovementUpdateQueued(PlayerInput input, PlayerInput.InputUpdate update) {
        if (!(update instanceof PlayerInput.SetMovementStates sms)) {
            return;
        }
        boolean j = sms.movementStates().jumping;
        S s = BY_INPUT.computeIfAbsent(input, k -> new S());
        synchronized (s) {
            if (j && !s.lastSmsJump) {
                s.risingPending = true;
            }
            s.lastSmsJump = j;
        }
    }

    /**
     * Last {@code jumping} from an SMS seen in {@link PlayerInput#queue} this session, or {@code null} if none yet
     * (mixin inactive or no SMS).
     */
    public static Boolean lastQueuedJumping(PlayerInput input) {
        if (input == null) {
            return null;
        }
        S s = BY_INPUT.get(input);
        if (s == null) {
            return null;
        }
        synchronized (s) {
            return s.lastSmsJump;
        }
    }

    /**
     * Whether a jump rising edge was observed on the raw SMS stream since last consume.
     */
    public static boolean consumeJumpRisingEdge(PlayerInput input) {
        if (input == null) {
            return false;
        }
        S s = BY_INPUT.get(input);
        if (s == null) {
            return false;
        }
        synchronized (s) {
            if (!s.risingPending) {
                return false;
            }
            s.risingPending = false;
            return true;
        }
    }
}
