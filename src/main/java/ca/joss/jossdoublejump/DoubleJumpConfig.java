package ca.joss.jossdoublejump;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.File;
import javax.annotation.Nullable;

public final class DoubleJumpConfig {
    /** Disk file under the server {@code mods/} folder. */
    public static final String CONFIG_FILE_NAME = "JossDoubleJumpConfig.json";

    private static final HytaleLogger LOGGER = Log.INSTANCE;
    private static DoubleJumpConfig instance;
    private static File configDir;
    private static Gson gson;
    public float horizontalBoost;
    public float verticalBoost;
    /** Small upward boost applied on the initial ground jump (after input), to make the liftoff a bit higher. */
    public float initialJumpBoostY = 1.0f;
    public long cooldownMs;
    public float staminaCost;
    public boolean usePercentageStamina;
    public float staminaLossPercentage;
    public boolean infiniteDoubleJump;
    public int maxJumps;
    /**
     * Total jump charges per ground contact (e.g. 2 = one consumed at liftoff, one left for the mod air jump). If 0,
     * {@link #maxJumps} legacy is used: {@code maxJumps + 1} (extra air jumps + liftoff).
     */
    public int jumpCharges;
    public boolean useAbility2;
    public boolean useAbility3;
    public boolean useJumpKey;

    /**
     * How long a queue-detected jump edge remains consumable in {@link DoubleJumpTicking.AfterInputSystem} (ms).
     * Shipped default 300 ms targets bursty real-server queues; lower (e.g. 160–220) if you want stricter edge timing.
     */
    public long queueJumpEdgeBufferMs = 300L;

    /**
     * Frames of input debounce after a second-jump request. Lower (e.g. 1) if taps feel ignored when spamming; higher
     * reduces duplicate triggers. Default 1 matches tuned responsiveness (legacy higher values ~4 → 1).
     */
    public int inputDebounceFrames = 1;

    /**
     * After leaving the ground, for this many ticks the mod pretends the jump signal is false for the <strong>input FSM
     * only</strong> ({@code effectiveSignal}) when using fallback or queue SMS paths — so HELD can become WAITING while
     * the key is still physically held. Double-jump detection uses <strong>unmasked</strong> {@code signal} for edges, so
     * this mask does not trigger a second jump when it expires. Ignored for raw or divergent pending-MS. To migrate old
     * tick counts: {@code (old + 1) / 3}.
     */
    public int postLiftoffJumpSignalIgnoreTicks = 6;

    /**
     * If true, a mod air jump requires {@link DoubleJumpComponent.InputState#WAITING_FOR_PRESS} and an unmasked rising edge
     * (release then press). If false, any unmasked false→true jump edge qualifies. Default false: the post-liftoff mask
     * only affects {@code effectiveSignal} for the input FSM, not edge detection (which uses unmasked {@code signal}).
     */
    public boolean requireReleaseForDoubleJump;

    /**
     * If &gt; 0: while air can double-jump, in {@link DoubleJumpComponent.InputState#WAITING_FOR_PRESS} at tick start, after
     * this many consecutive ticks with a seen signal-low since last ground, treat sustained jump signal as a second-jump
     * request even when {@code st.jumping} never produced a clean edge (fallback stuck high). Set 0 to disable. Requires
     * {@link #sawSignalLowWhileWaiting} so a full release still happened — does not repeat the pre-0.3.6 mask-expiry bug.
     */
    public int tapAssistMinWaitingTicks = 2;

    /**
     * After a real jump release ({@code HELD} with unmasked {@code signal == false}), how many ticks the mod watches
     * for a second-jump attempt that may not produce a visible {@code SetMovementStates} jump rising edge (short taps,
     * non-SMS-heavy queues). Set {@code 0} to disable {@link DoubleJumpTicking.AfterInputSystem} “queue burst” second
     * jump intent.
     */
    public int secondPressGraceTicks = 6;

    /**
     * Within {@link #secondPressGraceTicks}, require at least this many non-{@link com.hypixel.hytale.server.core.modules.entity.player.PlayerInput.SetMovementStates}
     * queue entries this tick (see {@link ca.joss.jossdoublejump.mixin.PlayerInputQueueMixin}) to treat the tick as a
     * possible second tap when SMS edges are missing. Raise if you see false positives (e.g. movement-heavy mods).
     */
    public int secondJumpMinNonSmsUpdates = 2;

    /**
     * Same window: minimum total {@link com.hypixel.hytale.server.core.modules.entity.player.PlayerInput#queue} calls
     * this tick (SMS + non-SMS). Keeps the path from firing on idle frames.
     */
    public int secondJumpMinTotalQueueUpdates = 6;

    /**
     * Temporary diagnostic: when true, jump-key airborne ticks log latch inputs ({@code signal}, {@code effectiveSignal},
     * edges, queue edge, release/pending flags, tryApply result). Disable for normal gameplay — high log volume.
     */
    public boolean traceLatchDiagnostics = false;

    /**
     * When true and a queue SMS sample exists (Hyxin {@code queue} inject and/or {@link ca.joss.jossdoublejump.mixin.PlayerInputJumpAuthorityMixin#feedFromQueueSmsWalk} from the pre-ProcessPlayerInput queue walk), jump-key mode uses that
     * client {@code MovementStates.jumping} stream as primary signal and strict second-press latching (no queue-edge /
     * carry for double). When false or before any SMS has been seen, the legacy reflection + queue + fallback chain is used.
     */
    public boolean authoritativeJumpKeyInput = true;

    /**
     * When true, emits {@code [DJ auth]}, {@code [DJ release]}, {@code [DJ second press]}, {@code [DJ latch source]} lines
     * for jump-key authority and latch decisions. Low volume compared to {@link #traceLatchDiagnostics}.
     */
    public boolean traceJumpAuthorityDiagnostics = false;

    /**
     * Informational only (persisted in JSON for operators). Not read by gameplay code. Jump-key detection assumes normal
     * survival-style movement; creative flight and similar modes often use different input and movement state.
     */
    public String usageNote;

    /** Resolved from config booleans (jump key wins over ability flags). */
    public enum ActivationMode {
        JUMP_KEY,
        ABILITY_2,
        ABILITY_3,
        NONE;

        public static ActivationMode from(@Nullable DoubleJumpConfig c) {
            if (c == null) {
                return NONE;
            }
            if (c.useJumpKey) {
                return JUMP_KEY;
            }
            if (c.useAbility2) {
                return ABILITY_2;
            }
            if (c.useAbility3) {
                return ABILITY_3;
            }
            return NONE;
        }

        public String uiLabel() {
            return switch (this) {
                case JUMP_KEY -> "Jump (air)";
                case ABILITY_2 -> "Ability 2";
                case ABILITY_3 -> "Ability 3";
                case NONE -> "None";
            };
        }

        public boolean usesAbilityInjection() {
            return this == ABILITY_2 || this == ABILITY_3;
        }
    }

    public static void load(File file, Gson gson) {
        configDir = file;
        DoubleJumpConfig.gson = gson;
        instance =
            DoubleJump.getConfigLoader()
                .load(DoubleJumpConfig.class, CONFIG_FILE_NAME, "double_jump_defaults.json", file, gson);
        ((HytaleLogger.Api) LOGGER.atInfo()).log("Loaded successfully");
    }

    public static void saveToDisk() {
        DoubleJump.getConfigLoader().save(new File(configDir, CONFIG_FILE_NAME), instance, gson);
    }

    public static DoubleJumpConfig get() {
        return instance;
    }

    /** Total charges while grounded / at reset; ignores value when {@link #infiniteDoubleJump} is true (handled elsewhere). */
    public int totalJumpCharges() {
        if (jumpCharges > 0) {
            return jumpCharges;
        }
        return maxJumps + 1;
    }
}
