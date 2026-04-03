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

    /** Input FSM for detecting the second jump request (separate from the phase/charges FSM). */
    public enum InputState {
        /** Airborne and waiting for a new press edge. */
        WAITING_FOR_PRESS,
        /** Jump signal is currently held down. */
        HELD,
        /** Short debounce after an edge so we don't re-trigger on jitter/dup packets. */
        COOLDOWN_FRAMES
    }

    /** Successful mod {@link ca.joss.jossdoublejump.DoubleJumpTicking#tryApply} calls this airtime (log / debug). */
    int jumpCount;
    long lastDoubleJumpTimeMs;

    /** Remaining jump charges until landing; reset on ground to {@link DoubleJumpConfig#totalJumpCharges()}. */
    int chargesRemaining;

    Phase phase = Phase.GROUNDED;

    /**
     * Input FSM state while airborne. Reset on landing.
     */
    InputState inputState = InputState.WAITING_FOR_PRESS;

    /** Last sampled jump signal (from raw input when available; else fallback). */
    boolean jumpSignalLast;

    /** Remaining debounce frames when {@link #inputState} is {@link InputState#COOLDOWN_FRAMES}. */
    int inputCooldownFramesRemaining;

    /**
     * End state of {@code jumping} after simulating {@link com.hypixel.hytale.server.core.modules.entity.player.PlayerInput}'s
     * {@code SetMovementStates} queue last tick — used as the baseline for the next tick's queue walk. Keeps edge
     * detection aligned with queued client updates instead of {@link MovementStatesComponent} pre-tick snapshots (which
     * can disagree with the queue for the same frame).
     */
    boolean jumpHeldLastQueue;

    /**
     * True if {@link PlayerInput#getMovementUpdateQueue()} contained at least one {@link PlayerInput.SetMovementStates}
     * this tick — used so {@link ca.joss.jossdoublejump.mixin.PlayerInputQueueMixin}'s jump bit is not mistaken for
     * authoritative when the queue was all velocity/look packets (Zephyr spam).
     */
    boolean movementQueueHadSms;

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
        c.inputState = this.inputState;
        c.jumpSignalLast = this.jumpSignalLast;
        c.inputCooldownFramesRemaining = this.inputCooldownFramesRemaining;
        c.jumpHeldLastQueue = this.jumpHeldLastQueue;
        c.movementQueueHadSms = this.movementQueueHadSms;
        c.pendingQueueJumpEdge = this.pendingQueueJumpEdge;
        c.queueJumpEdgeBufferUntilMs = this.queueJumpEdgeBufferUntilMs;
        return c;
    }
}
