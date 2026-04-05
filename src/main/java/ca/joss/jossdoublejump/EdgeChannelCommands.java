package ca.joss.jossdoublejump;

import ca.joss.jossdoublejump.edge.JumpEdgeIngress;
import ca.joss.jossdoublejump.edge.JumpEdgeMessage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Debug: inject synthetic edge messages (same queue as {@link ca.joss.jossdoublejump.mixin.PageManagerMixin}) for
 * transport proof without a client mod. Optional argument: UP or DOWN (default DOWN).
 */
public final class EdgeChannelCommands {

    private EdgeChannelCommands() {}

    public static final class EdgeTest extends AbstractPlayerCommand {
        public EdgeTest() {
            super("jossedgetest", "JossDoubleJump: queue one synthetic jump edge for transport testing");
            this.setPermissionGroup(GameMode.Creative);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            String in = context.getInputString() != null ? context.getInputString().trim() : "";
            String t = in.equalsIgnoreCase("UP") ? "UP" : "DOWN";
            long seq = System.nanoTime();
            JumpEdgeIngress.injectForTesting(playerRef, new JumpEdgeMessage(1, seq, t));
            context.sendMessage(Message.raw("Queued jdj edge test: " + t + " seq=" + seq));
        }
    }
}
