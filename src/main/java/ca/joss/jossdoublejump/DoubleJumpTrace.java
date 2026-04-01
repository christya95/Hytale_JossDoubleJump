package ca.joss.jossdoublejump;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Debug logging for a single username (movement / jump paths only). */
final class DoubleJumpTrace {

    private static final String USERNAME = "iRespect";
    private static final HytaleLogger LOG = Log.INSTANCE;

    private DoubleJumpTrace() {}

    static boolean is(Ref<EntityStore> ref, CommandBuffer<EntityStore> cmd) {
        if (ref == null || !ref.isValid() || cmd == null) {
            return false;
        }
        PlayerRef pr = (PlayerRef) cmd.getComponent(ref, PlayerRef.getComponentType());
        return pr != null && USERNAME.equals(pr.getUsername());
    }

    static void log(Ref<EntityStore> ref, CommandBuffer<EntityStore> cmd, String message) {
        if (is(ref, cmd)) {
            ((HytaleLogger.Api) LOG.atInfo()).log("[DJ trace %s] %s", USERNAME, message);
        }
    }
}
