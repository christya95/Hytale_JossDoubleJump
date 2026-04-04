package ca.joss.jossdoublejump.mixin;

import amore.servercomm.capture.InputQueueIngress;
import amore.servercomm.registry.PerPlayerTraceState;
import amore.servercomm.registry.ServerCommRegistry;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records each {@link PlayerInput.SetMovementStates} as it enters the queue, before movement merge. Tracker state lives
 * in this class (not a separate package) so Hyxin’s {@code TransformingClassLoader} can resolve bytecode when the
 * injected handler runs.
 */
@Mixin(PlayerInput.class)
public class PlayerInputQueueMixin {

    private static final class S {
        boolean lastSmsJump;
        boolean risingPending;
        /** Per-tick: total {@link PlayerInput#queue} calls; reset when snapshot in {@link #takeQueueActivitySnapshot}. */
        int totalQueuedThisTick;
        /** Per-tick: queue entries that are not {@link PlayerInput.SetMovementStates}. */
        int nonSmsQueuedThisTick;
    }

    private static final Map<PlayerInput, S> BY_INPUT = Collections.synchronizedMap(new WeakHashMap<>());

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

    /**
     * Returns queue activity since the last snapshot, then clears counters. Call once per tick from
     * {@link ca.joss.jossdoublejump.DoubleJumpTicking.QueueScannerSystem} before {@code st.jumping} is used — captures
     * traffic that may not include a {@link PlayerInput.SetMovementStates} jump rising edge (short second taps).
     *
     * @param outTotalNonSms output {@code [0] = total queue calls, [1] = non-SMS queue calls}
     */
    public static void takeQueueActivitySnapshot(PlayerInput input, int[] outTotalNonSms) {
        if (input == null || outTotalNonSms == null || outTotalNonSms.length < 2) {
            return;
        }
        S s = BY_INPUT.get(input);
        if (s == null) {
            outTotalNonSms[0] = 0;
            outTotalNonSms[1] = 0;
            return;
        }
        synchronized (s) {
            outTotalNonSms[0] = s.totalQueuedThisTick;
            outTotalNonSms[1] = s.nonSmsQueuedThisTick;
            s.totalQueuedThisTick = 0;
            s.nonSmsQueuedThisTick = 0;
        }
    }

    @Inject(method = "queue", at = @At("HEAD"))
    private void jossDoubleJump$onInputQueued(PlayerInput.InputUpdate update, CallbackInfo ci) {
        PlayerInput self = (PlayerInput) (Object) this;
        PerPlayerTraceState amoreSt = ServerCommRegistry.stateFor(self);
        amoreSt.lastIngressNano = System.nanoTime();
        InputQueueIngress.record(amoreSt.mailbox, update);
        S s = BY_INPUT.computeIfAbsent(self, k -> new S());
        synchronized (s) {
            s.totalQueuedThisTick++;
            if (!(update instanceof PlayerInput.SetMovementStates sms)) {
                s.nonSmsQueuedThisTick++;
            } else {
                boolean j = sms.movementStates().jumping;
                if (j && !s.lastSmsJump) {
                    s.risingPending = true;
                }
                s.lastSmsJump = j;
            }
        }
    }
}
