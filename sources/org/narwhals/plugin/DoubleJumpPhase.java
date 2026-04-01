package org.narwhals.plugin;

/**
 * High-level locomotion state for the mod extra air jump(s). Vanilla ground jump is unchanged.
 *
 * <ul>
 *   <li>{@link #GROUNDED} — on ground (or treated as grounded: fluid/climb per {@link DoubleJumpSystem}).</li>
 *   <li>{@link #AIR_CAN_DOUBLE} — airborne; mod may still grant extra jump(s) per {@link DoubleJumpConfig#maxJumps}.</li>
 *   <li>{@link #AIR_SPENT} — airborne; used all extra jumps for this airtime (until landing).</li>
 * </ul>
 */
public enum DoubleJumpPhase {
    GROUNDED,
    AIR_CAN_DOUBLE,
    AIR_SPENT
}
