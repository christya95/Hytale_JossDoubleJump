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

    /** Extra mod jumps used this airtime; compared to {@link DoubleJumpConfig#maxJumps}. */
    int jumpCount;
    long lastDoubleJumpTimeMs;

    Phase phase = Phase.GROUNDED;

    /**
     * Set by the queue scanner when a jump press edge appears in the raw movement queue while simulated airborne.
     * Consumed by the after-input tick.
     */
    boolean airJumpPressPending;

    @Nonnull
    public DoubleJumpComponent clone() {
        DoubleJumpComponent c = new DoubleJumpComponent();
        c.jumpCount = this.jumpCount;
        c.lastDoubleJumpTimeMs = this.lastDoubleJumpTimeMs;
        c.phase = this.phase;
        c.airJumpPressPending = this.airJumpPressPending;
        return c;
    }
}
