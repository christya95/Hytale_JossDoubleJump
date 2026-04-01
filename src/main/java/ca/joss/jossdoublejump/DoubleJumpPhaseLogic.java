package ca.joss.jossdoublejump;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

/**
 * Central {@link DoubleJumpPhase} transitions. Vanilla first jump is unchanged; this only governs
 * mod extra air jumps and the jump-key queue edge ({@link DoubleJumpComponent#airJumpPressPending}).
 *
 * <pre>
 * GROUNDED ──(leave ground)──▶ AIR_CAN_DOUBLE ──(use mod jump(s) until cap)──▶ AIR_SPENT
 *     ▲                              │                    │
 *     └────────────(land / fluid / climb)─────────────────┘
 * </pre>
 */
public final class DoubleJumpPhaseLogic {

    private DoubleJumpPhaseLogic() {}

    /**
     * Called every tick while the player has ground contact (including fluid and climbing — same as
     * {@link com.hypixel.hytale.protocol.MovementStates} “grounded” checks in {@link DoubleJumpSystem}).
     */
    public static void onGrounded(DoubleJumpComponent dj) {
        dj.phase = DoubleJumpPhase.GROUNDED;
        dj.jumpCount = 0;
        dj.airJumpPressPending = false;
    }

    /**
     * Called each tick the player is airborne (after {@link #onGrounded} was not taken). Establishes
     * {@link DoubleJumpPhase#AIR_CAN_DOUBLE} on the first tick off the ground, then enforces
     * {@link DoubleJumpPhase#AIR_SPENT} when extra jumps are exhausted.
     */
    public static void tickAirborne(DoubleJumpComponent dj, @Nullable DoubleJumpConfig cfg) {
        if (cfg == null) {
            return;
        }
        if (dj.phase == DoubleJumpPhase.GROUNDED) {
            dj.phase = DoubleJumpPhase.AIR_CAN_DOUBLE;
        }
        if (!cfg.infiniteDoubleJump && dj.jumpCount >= cfg.maxJumps) {
            dj.phase = DoubleJumpPhase.AIR_SPENT;
        }
    }

    /**
     * Jump-key mode: consumes at most one queued air jump press. Always clears the pending flag when
     * set. Only calls {@link DoubleJumpExecutor} in {@link DoubleJumpPhase#AIR_CAN_DOUBLE}.
     *
     * @return true if {@link DoubleJumpExecutor#tryApply} ran and returned true
     */
    public static boolean consumeAirJumpPressIfPending(
        Ref<EntityStore> ref,
        CommandBuffer<EntityStore> cmd,
        DoubleJumpComponent dj,
        DoubleJumpConfig cfg
    ) {
        if (!dj.airJumpPressPending) {
            return false;
        }
        dj.airJumpPressPending = false;
        if (dj.phase != DoubleJumpPhase.AIR_CAN_DOUBLE) {
            return false;
        }
        return DoubleJumpExecutor.tryApply(ref, cmd, dj, cfg);
    }
}
