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

    /**
     * Previous tick's <strong>unmasked</strong> jump {@code signal} (see {@link ca.joss.jossdoublejump.DoubleJumpTicking.AfterInputSystem}).
     * Used only for rising-edge detection; must not follow the post-liftoff mask or a held key looks like a new press when the mask ends.
     */
    boolean rawSignalLast;

    /**
     * Consecutive ticks (while {@link InputState#WAITING_FOR_PRESS} at tick start and air can still double-jump) for tap assist.
     */
    int ticksWaitingForSecondJump;

    /** True once the unmasked signal has gone false while {@link #ticksWaitingForSecondJump} was counting (release seen). */
    boolean sawSignalLowWhileWaiting;

    /** After a successful mod jump via tap assist (infinite mode); blocks a second tap assist in the same airborne period. */
    boolean tapAssistConsumedThisAirborne;

    /** After {@link DoubleJumpTicking.AfterInputSystem} “queue burst” second jump; one per airborne period. */
    boolean secondJumpSyntheticConsumedThisAirborne;

    /**
     * Ticks left to accept a queue-burst second jump after {@link InputState#HELD} with unmasked {@code signal == false}
     * (real release). See {@link DoubleJumpConfig#secondPressGraceTicks}.
     */
    int secondPressGraceTicksRemaining;

    /**
     * Snapshot from {@link ca.joss.jossdoublejump.mixin.PlayerInputQueueMixin#takeQueueActivitySnapshot} for this tick
     * (before queue walk).
     */
    int totalQueueUpdatesThisTick;

    /** Non-SMS queue entries this tick (companion to {@link #totalQueueUpdatesThisTick}). */
    int nonSmsQueueUpdatesThisTick;

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

    /**
     * While airborne after ground liftoff: ticks remaining where {@link MovementStatesComponent}’s processed
     * {@code jumping} is treated as false for FSM/edge purposes when the mod falls back to that bit (stuck high while the
     * key is held). See {@link DoubleJumpConfig#postLiftoffJumpSignalIgnoreTicks}.
     */
    int postLiftoffSignalMaskTicksRemaining;

    @Nonnull
    public DoubleJumpComponent clone() {
        DoubleJumpComponent c = new DoubleJumpComponent();
        c.jumpCount = this.jumpCount;
        c.lastDoubleJumpTimeMs = this.lastDoubleJumpTimeMs;
        c.chargesRemaining = this.chargesRemaining;
        c.phase = this.phase;
        c.inputState = this.inputState;
        c.rawSignalLast = this.rawSignalLast;
        c.ticksWaitingForSecondJump = this.ticksWaitingForSecondJump;
        c.sawSignalLowWhileWaiting = this.sawSignalLowWhileWaiting;
        c.tapAssistConsumedThisAirborne = this.tapAssistConsumedThisAirborne;
        c.secondJumpSyntheticConsumedThisAirborne = this.secondJumpSyntheticConsumedThisAirborne;
        c.secondPressGraceTicksRemaining = this.secondPressGraceTicksRemaining;
        c.totalQueueUpdatesThisTick = this.totalQueueUpdatesThisTick;
        c.nonSmsQueueUpdatesThisTick = this.nonSmsQueueUpdatesThisTick;
        c.inputCooldownFramesRemaining = this.inputCooldownFramesRemaining;
        c.jumpHeldLastQueue = this.jumpHeldLastQueue;
        c.movementQueueHadSms = this.movementQueueHadSms;
        c.pendingQueueJumpEdge = this.pendingQueueJumpEdge;
        c.queueJumpEdgeBufferUntilMs = this.queueJumpEdgeBufferUntilMs;
        c.postLiftoffSignalMaskTicksRemaining = this.postLiftoffSignalMaskTicksRemaining;
        return c;
    }
}
