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
 * Tracks client jump intent from {@link PlayerInput#queue} ingress and from the pre-ProcessPlayerInput SMS queue walk.
 * {@link PlayerInput} has no separate keyboard field; {@link PlayerInput.SetMovementStates} (and rider SMS) carry the
 * same {@link com.hypixel.hytale.protocol.MovementStates#jumping} bit before merge into {@code MovementStatesComponent}.
 */
@Mixin(PlayerInput.class)
public class PlayerInputJumpAuthorityMixin {

    /** How {@link #authorityInitialized} was last satisfied (for diagnostics). */
    public enum AuthorityFeed {
        NONE,
        QUEUE_INJECT,
        SCANNER_WALK
    }

    private static final class A {
        /** At least one authoritative sample (inject or scanner) has been recorded for this input. */
        boolean authorityInitialized;
        /** Last client-side jump held from SMS / rider SMS (inject tail or scanner walk end state). */
        boolean lastSmsJumpDown;
        /** {@link #lastSmsJumpDown} at end of previous {@link ca.joss.jossdoublejump.DoubleJumpTicking.AfterInputSystem} tick. */
        boolean prevTickJumpDown;
        /** Per tick: computed in {@link #prepareAfterInputTick}. */
        boolean risingThisTick;
        boolean fallingThisTick;
        AuthorityFeed lastFeed = AuthorityFeed.NONE;
    }

    private static final Map<PlayerInput, A> BY_INPUT = Collections.synchronizedMap(new WeakHashMap<>());

    private static void applyMovementStatesJump(PlayerInput self, boolean jumping, AuthorityFeed feed) {
        A a = BY_INPUT.computeIfAbsent(self, k -> new A());
        synchronized (a) {
            a.authorityInitialized = true;
            a.lastSmsJumpDown = jumping;
            a.lastFeed = feed;
        }
    }

    @Inject(method = "queue", at = @At("HEAD"))
    private void jossDoubleJump$jumpAuthOnQueued(PlayerInput.InputUpdate update, CallbackInfo ci) {
        PlayerInput self = (PlayerInput) (Object) this;
        if (update instanceof PlayerInput.SetMovementStates sms) {
            applyMovementStatesJump(self, sms.movementStates().jumping, AuthorityFeed.QUEUE_INJECT);
            return;
        }
        if (update instanceof PlayerInput.SetRiderMovementStates rms) {
            applyMovementStatesJump(self, rms.movementStates().jumping, AuthorityFeed.QUEUE_INJECT);
        }
    }

    /**
     * Called from {@link ca.joss.jossdoublejump.DoubleJumpTicking.QueueScannerSystem} after walking the movement queue
     * for SMS — guarantees authority even if the Hyxin {@code queue} inject is absent or not firing for this build.
     */
    public static void feedFromQueueSmsWalk(PlayerInput input, boolean lastJumpHeldFromSmsWalk, boolean hadSmsInWalk) {
        if (input == null || !hadSmsInWalk) {
            return;
        }
        applyMovementStatesJump(input, lastJumpHeldFromSmsWalk, AuthorityFeed.SCANNER_WALK);
    }

    /**
     * Call at the start of each airborne {@code AfterInputSystem} tick (after grounded early-return is decided).
     * Prepares tick-local rising/falling vs last tick’s end state for diagnostics.
     */
    public static void prepareAfterInputTick(PlayerInput input) {
        if (input == null) {
            return;
        }
        A a = BY_INPUT.get(input);
        if (a == null) {
            return;
        }
        synchronized (a) {
            a.risingThisTick = a.lastSmsJumpDown && !a.prevTickJumpDown;
            a.fallingThisTick = !a.lastSmsJumpDown && a.prevTickJumpDown;
        }
    }

    /** Call at the end of each {@code AfterInputSystem} tick while processing this player. */
    public static void finishAfterInputTick(PlayerInput input) {
        if (input == null) {
            return;
        }
        A a = BY_INPUT.get(input);
        if (a == null) {
            return;
        }
        synchronized (a) {
            a.prevTickJumpDown = a.lastSmsJumpDown;
        }
    }

    /**
     * When landing, align previous-tick snapshot so the next airborne tick does not see a spurious edge vs ground
     * handling.
     */
    public static void syncAfterGrounded(PlayerInput input) {
        if (input == null) {
            return;
        }
        A a = BY_INPUT.get(input);
        if (a == null) {
            return;
        }
        synchronized (a) {
            a.prevTickJumpDown = a.lastSmsJumpDown;
        }
    }

    /** {@code null} until at least one inject or {@link #feedFromQueueSmsWalk} sample exists for this input. */
    public static Boolean authoritativeJumpPressed(PlayerInput input) {
        if (input == null) {
            return null;
        }
        A a = BY_INPUT.get(input);
        if (a == null || !a.authorityInitialized) {
            return null;
        }
        synchronized (a) {
            return a.lastSmsJumpDown;
        }
    }

    public static boolean hasAuthoritativeJumpSample(PlayerInput input) {
        if (input == null) {
            return false;
        }
        A a = BY_INPUT.get(input);
        if (a == null) {
            return false;
        }
        synchronized (a) {
            return a.authorityInitialized;
        }
    }

    /** @deprecated use {@link #hasAuthoritativeJumpSample} */
    @Deprecated
    public static boolean hasAuthoritativeSmsSample(PlayerInput input) {
        return hasAuthoritativeJumpSample(input);
    }

    public static AuthorityFeed lastAuthorityFeed(PlayerInput input) {
        if (input == null) {
            return AuthorityFeed.NONE;
        }
        A a = BY_INPUT.get(input);
        if (a == null) {
            return AuthorityFeed.NONE;
        }
        synchronized (a) {
            return a.lastFeed;
        }
    }

    /** One-line for {@code traceJumpAuthorityDiagnostics}. */
    public static String authorityDebug(PlayerInput input) {
        if (input == null) {
            return "input=null";
        }
        A a = BY_INPUT.get(input);
        if (a == null) {
            return "noState";
        }
        synchronized (a) {
            return String.format(
                "init=%b jumpDown=%b feed=%s",
                a.authorityInitialized,
                a.lastSmsJumpDown,
                a.lastFeed.name());
        }
    }

    public static boolean authoritativeRisingThisTick(PlayerInput input) {
        if (input == null) {
            return false;
        }
        A a = BY_INPUT.get(input);
        if (a == null) {
            return false;
        }
        synchronized (a) {
            return a.risingThisTick;
        }
    }

    public static boolean authoritativeFallingThisTick(PlayerInput input) {
        if (input == null) {
            return false;
        }
        A a = BY_INPUT.get(input);
        if (a == null) {
            return false;
        }
        synchronized (a) {
            return a.fallingThisTick;
        }
    }
}
