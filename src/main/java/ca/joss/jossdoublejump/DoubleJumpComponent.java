package ca.joss.jossdoublejump;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class DoubleJumpComponent implements Component<EntityStore> {
    /** Locomotion state for mod extra air jumps (vanilla ground jump unchanged). */
    public enum Phase {
        GROUNDED,
        AIR_CAN_DOUBLE,
        AIR_SPENT
    }

    /** Successful mod {@link ca.joss.jossdoublejump.DoubleJumpTicking#tryApply} calls this airtime (log / debug). */
    int jumpCount;
    long lastDoubleJumpTimeMs;

    /** Remaining jump charges until landing; reset on ground to {@link DoubleJumpConfig#totalJumpCharges()}. */
    int chargesRemaining;

    Phase phase = Phase.GROUNDED;

    /**
     * Previous tick's processed {@link com.hypixel.hytale.protocol.MovementStates#jumping} (after
     * {@link com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems.ProcessPlayerInput}), for rising-edge
     * detection. Updated on grounded and each airborne tick.
     */
    boolean jumpPressedLastAfterInput;

    /**
     * End state of {@code jumping} after simulating {@link com.hypixel.hytale.server.core.modules.entity.player.PlayerInput}'s
     * {@code SetMovementStates} queue last tick — used as the baseline for the next tick's queue walk. Keeps edge
     * detection aligned with queued client updates instead of {@link MovementStatesComponent} pre-tick snapshots (which
     * can disagree with the queue for the same frame).
     */
    boolean jumpHeldLastQueue;

    /**
     * While airborne: set {@link #jumpReleasedSinceAirborne} only after we saw a jump press in air, then release — avoids
     * treating ledge-fall as "released" and gates the mod double jump to release-then-press.
     */
    boolean hadJumpPressWhileAirborne;

    /** Airborne only: true after {@link #hadJumpPressWhileAirborne} and then a processed jump release. Reset on landing. */
    boolean jumpReleasedSinceAirborne;

    /**
     * Set when the queue walk sees a jump rising edge; optional time buffer extends consumption into {@link DoubleJumpTicking.AfterInputSystem}.
     */
    boolean pendingQueueJumpEdge;

    /** Wall-clock until which a queue jump edge may still be consumed (buffer if AfterInput runs slightly late). */
    long queueJumpEdgeBufferUntilMs;

    @Nonnull
    public DoubleJumpComponent clone() {
        DoubleJumpComponent c = new DoubleJumpComponent();
        c.jumpCount = this.jumpCount;
        c.lastDoubleJumpTimeMs = this.lastDoubleJumpTimeMs;
        c.chargesRemaining = this.chargesRemaining;
        c.phase = this.phase;
        c.jumpPressedLastAfterInput = this.jumpPressedLastAfterInput;
        c.jumpHeldLastQueue = this.jumpHeldLastQueue;
        c.hadJumpPressWhileAirborne = this.hadJumpPressWhileAirborne;
        c.jumpReleasedSinceAirborne = this.jumpReleasedSinceAirborne;
        c.pendingQueueJumpEdge = this.pendingQueueJumpEdge;
        c.queueJumpEdgeBufferUntilMs = this.queueJumpEdgeBufferUntilMs;
        return c;
    }
}
