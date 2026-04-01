package ca.joss.jossdoublejump.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import ca.joss.jossdoublejump.DoubleJump;
import ca.joss.jossdoublejump.DoubleJumpConfig;
import ca.joss.jossdoublejump.DoubleJumpLogger;

public class DoubleJumpAdminPage extends InteractiveCustomUIPage<DoubleJumpAdminPageEventData> {
    private static final HytaleLogger LOGGER = DoubleJumpLogger.get();

    public DoubleJumpAdminPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, DoubleJumpAdminPageEventData.CODEC);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        commands.append("Pages/DoubleJumpAdmin.ui");
        commands.set("#InfoLabel.Text", DoubleJump.getVersion() + " - Admin");
        DoubleJumpConfig config = DoubleJumpConfig.get();
        commands.set("#DJHorizontalBoostInput.Value", config.horizontalBoost);
        commands.set("#DJVerticalBoostInput.Value", config.verticalBoost);
        commands.set("#DJCooldownInput.Value", (float) config.cooldownMs);
        commands.set("#DJStaminaCostInput.Value", config.staminaCost);
        commands.set("#DJUsePercentageStaminaCheckBox.Value", config.usePercentageStamina);
        commands.set("#DJStaminaLossPercentageInput.Value", config.staminaLossPercentage);
        commands.set("#InfiniteDoubleJumpCheckBox.Value", config.infiniteDoubleJump);
        commands.set("#DJMaxJumpsInput.Value", config.maxJumps);
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SaveButton",
            new EventData()
                .append("@djHorizontalBoost", "#DJHorizontalBoostInput.Value")
                .append("@djVerticalBoost", "#DJVerticalBoostInput.Value")
                .append("@djCooldownMs", "#DJCooldownInput.Value")
                .append("@djStaminaCost", "#DJStaminaCostInput.Value")
                .append("@djUsePercentageStamina", "#DJUsePercentageStaminaCheckBox.Value")
                .append("@djStaminaLossPercentage", "#DJStaminaLossPercentageInput.Value")
                .append("@infiniteDoubleJump", "#InfiniteDoubleJumpCheckBox.Value")
                .append("@djMaxJumps", "#DJMaxJumpsInput.Value"));
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull DoubleJumpAdminPageEventData data
    ) {
        DoubleJumpConfig config = DoubleJumpConfig.get();
        config.horizontalBoost = (float) data.djHorizontalBoost;
        config.verticalBoost = (float) data.djVerticalBoost;
        config.cooldownMs = (long) data.djCooldownMs;
        config.staminaCost = (float) data.djStaminaCost;
        config.usePercentageStamina = data.djUsePercentageStamina;
        config.staminaLossPercentage = (float) data.djStaminaLossPercentage;
        config.infiniteDoubleJump = data.infiniteDoubleJump;
        config.maxJumps = (int) data.djMaxJumps;
        DoubleJumpConfig.saveToDisk();
        ((HytaleLogger.Api) LOGGER.atFine()).log("Double Jump: Settings saved and applied");
        this.close();
    }
}
