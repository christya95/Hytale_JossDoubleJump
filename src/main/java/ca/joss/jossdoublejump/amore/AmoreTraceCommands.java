package ca.joss.jossdoublejump.amore;

import amore.servercomm.api.ServerComm;
import amore.servercomm.dump.TraceDumpService;
import amore.servercomm.registry.PerPlayerTraceState;
import amore.servercomm.registry.ServerCommRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import javax.annotation.Nonnull;

/** Commands to toggle Amore tracing and write NDJSON / Chrome / CBOR dumps under {@code mods/amore-traces/}. */
public final class AmoreTraceCommands {

    private static final HytaleLogger LOG = HytaleLogger.get("DoubleJump");

    private AmoreTraceCommands() {}

    public static final class TraceOn extends AbstractPlayerCommand {
        public TraceOn() {
            super("amoretraceon", "Enable Amore movement trace for your session");
            this.setPermissionGroup(GameMode.Adventure);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            ServerComm.setTracing(playerRef.getUsername(), true);
            ((HytaleLogger.Api) LOG.atInfo()).log("Amore trace ON for %s", playerRef.getUsername());
        }
    }

    public static final class TraceOff extends AbstractPlayerCommand {
        public TraceOff() {
            super("amoretraceoff", "Disable Amore movement trace");
            this.setPermissionGroup(GameMode.Adventure);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            ServerComm.setTracing(playerRef.getUsername(), false);
            ((HytaleLogger.Api) LOG.atInfo()).log("Amore trace OFF for %s", playerRef.getUsername());
        }
    }

    public static final class DumpNdjson extends AbstractPlayerCommand {
        public DumpNdjson() {
            super("amoretracedumpndjson", "Write last ~30s of trace as NDJSON to mods/amore-traces/");
            this.setPermissionGroup(GameMode.Adventure);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            dump(store, ref, playerRef, DumpFormat.NDJSON);
        }
    }

    public static final class DumpPerfetto extends AbstractPlayerCommand {
        public DumpPerfetto() {
            super("amoretracedumptrace", "Write last ~30s of trace as Chrome JSON to mods/amore-traces/");
            this.setPermissionGroup(GameMode.Adventure);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            dump(store, ref, playerRef, DumpFormat.PERFETTO_JSON);
        }
    }

    public static final class DumpCbor extends AbstractPlayerCommand {
        public DumpCbor() {
            super("amoretracedumpcbor", "Write last ~30s of trace as CBOR array to mods/amore-traces/");
            this.setPermissionGroup(GameMode.Adventure);
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            dump(store, ref, playerRef, DumpFormat.CBOR);
        }
    }

    private enum DumpFormat {
        NDJSON,
        PERFETTO_JSON,
        CBOR
    }

    private static void dump(
        Store<EntityStore> store,
        Ref<EntityStore> ref,
        PlayerRef playerRef,
        DumpFormat format
    ) {
        PlayerInput input = (PlayerInput) store.getComponent(ref, PlayerInput.getComponentType());
        if (input == null) {
            ((HytaleLogger.Api) LOG.atWarning()).log("Amore dump: no PlayerInput");
            return;
        }
        PerPlayerTraceState st = ServerCommRegistry.stateFor(input);
        String user = playerRef.getUsername();
        int seconds = 30;
        try {
            Path path =
                switch (format) {
                    case NDJSON -> TraceDumpService.dumpNdjson(st, user, seconds);
                    case PERFETTO_JSON -> TraceDumpService.dumpPerfetto(st, user, seconds);
                    case CBOR -> TraceDumpService.dumpCbor(st, user, seconds);
                };
            ((HytaleLogger.Api) LOG.atInfo()).log("Amore dump written: %s", path.toString());
        } catch (Exception e) {
            ((HytaleLogger.Api) LOG.atWarning()).log("Amore dump failed: %s", e.getMessage());
        }
    }
}
