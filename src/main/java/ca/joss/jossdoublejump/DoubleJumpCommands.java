package ca.joss.jossdoublejump;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public final class DoubleJumpCommands {

    private DoubleJumpCommands() {}

    public static final class Mod extends AbstractPlayerCommand {
        public Mod() {
            super("doublejump", "Check plugin settings");
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
            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                DoubleJumpUi.ConfigPage page = new DoubleJumpUi.ConfigPage(playerRef);
                player.getPageManager().openCustomPage(ref, store, (CustomUIPage) page);
            }
        }
    }

    public static final class Settings extends AbstractPlayerCommand {
        public Settings() {
            super("doublejumpsettings", "Admin settings for Double Jump config");
        }

        @Override
        protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
        ) {
            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                DoubleJumpUi.AdminPage page = new DoubleJumpUi.AdminPage(playerRef);
                player.getPageManager().openCustomPage(ref, store, (CustomUIPage) page);
            }
        }
    }
}
