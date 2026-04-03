package ca.joss.jossdoublejump.mixin;

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
    }

    private static final Map<PlayerInput, S> BY_INPUT = Collections.synchronizedMap(new WeakHashMap<>());

    /** Called from the inject below; also used by {@link ca.joss.jossdoublejump.DoubleJumpTicking}. */
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

    @Inject(method = "queue", at = @At("HEAD"))
    private void jossDoubleJump$onInputQueued(PlayerInput.InputUpdate update, CallbackInfo ci) {
        onMovementUpdateQueued((PlayerInput) (Object) this, update);
    }
}
