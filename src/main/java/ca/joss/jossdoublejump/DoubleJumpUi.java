package ca.joss.jossdoublejump;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import javax.annotation.Nonnull;

/** In-game UI for viewing config and admin editing. */
public final class DoubleJumpUi {

    private static final String NA = "N/A";
    private static final HytaleLogger LOGGER = Log.INSTANCE;

    private DoubleJumpUi() {}

    public static final class ConfigPage extends CustomUIPage {

        public ConfigPage(@Nonnull PlayerRef player) {
            super(player, CustomPageLifetime.CanDismiss);
        }

        @Override
        public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder c,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store
        ) {
            c.append("Pages/DoubleJump.ui");
            c.set("#InfoLabel.Text", DoubleJump.getVersion());
            DoubleJumpConfig cfg = DoubleJumpConfig.get();
            if (cfg == null) {
                c.set("#DJHorizontalBoostValue.Text", NA);
                c.set("#DJVerticalBoostValue.Text", NA);
                c.set("#DJCooldownValue.Text", NA);
                c.set("#DJStaminaCostTypeValue.Text", NA);
                c.set("#DJStaminaCostValue.Text", NA);
                c.set("#InfiniteDoubleJumpValue.Text", NA);
                c.set("#DJMaxJumpsValue.Text", NA); // UI asset label ID; value is jump charges per ground reset
                c.set("#ActivationMethodValue.Text", NA);
                return;
            }

            c.set("#DJHorizontalBoostValue.Text", String.format(Locale.US, "%.1f", cfg.horizontalBoost));
            c.set("#DJVerticalBoostValue.Text", String.format(Locale.US, "%.1f", cfg.verticalBoost));
            c.set("#DJCooldownValue.Text", cfg.cooldownMs + "ms");
            c.set("#DJStaminaCostTypeValue.Text", cfg.usePercentageStamina ? "Percentage" : "Flat");
            c.set(
                "#DJStaminaCostValue.Text",
                cfg.usePercentageStamina
                    ? String.format(Locale.US, "%.1f%%", cfg.staminaLossPercentage)
                    : String.format(Locale.US, "%.1f", cfg.staminaCost));
            c.set("#InfiniteDoubleJumpValue.Text", cfg.infiniteDoubleJump ? "Enabled" : "Disabled");
            c.set(
                "#DJMaxJumpsValue.Text",
                cfg.infiniteDoubleJump
                    ? "Unlimited"
                    : String.valueOf(cfg.totalJumpCharges()));
            c.set("#ActivationMethodValue.Text", DoubleJumpConfig.ActivationMode.from(cfg).uiLabel());
        }
    }

    public static final class AdminEventData {
        public static final BuilderCodec<AdminEventData> CODEC =
            BuilderCodec.builder(AdminEventData.class, AdminEventData::new)
                .append(
                    new KeyedCodec<>("@djHorizontalBoost", Codec.DOUBLE),
                    (AdminEventData d, Double v) -> d.djHorizontalBoost = v,
                    d -> Double.valueOf(d.djHorizontalBoost))
                .add()
                .append(
                    new KeyedCodec<>("@djVerticalBoost", Codec.DOUBLE),
                    (AdminEventData d, Double v) -> d.djVerticalBoost = v,
                    d -> Double.valueOf(d.djVerticalBoost))
                .add()
                .append(
                    new KeyedCodec<>("@djCooldownMs", Codec.DOUBLE),
                    (AdminEventData d, Double v) -> d.djCooldownMs = v,
                    d -> Double.valueOf(d.djCooldownMs))
                .add()
                .append(
                    new KeyedCodec<>("@djStaminaCost", Codec.DOUBLE),
                    (AdminEventData d, Double v) -> d.djStaminaCost = v,
                    d -> Double.valueOf(d.djStaminaCost))
                .add()
                .append(
                    new KeyedCodec<>("@djUsePercentageStamina", Codec.BOOLEAN),
                    (AdminEventData d, Boolean v) -> d.djUsePercentageStamina = v,
                    d -> Boolean.valueOf(d.djUsePercentageStamina))
                .add()
                .append(
                    new KeyedCodec<>("@djStaminaLossPercentage", Codec.DOUBLE),
                    (AdminEventData d, Double v) -> d.djStaminaLossPercentage = v,
                    d -> Double.valueOf(d.djStaminaLossPercentage))
                .add()
                .append(
                    new KeyedCodec<>("@infiniteDoubleJump", Codec.BOOLEAN),
                    (AdminEventData d, Boolean v) -> d.infiniteDoubleJump = v,
                    d -> Boolean.valueOf(d.infiniteDoubleJump))
                .add()
                .append(
                    new KeyedCodec<>("@djMaxJumps", Codec.DOUBLE),
                    (AdminEventData d, Double v) -> d.djJumpCharges = v,
                    d -> Double.valueOf(d.djJumpCharges))
                .add()
                .build();

        public double djHorizontalBoost;
        public double djVerticalBoost;
        public double djCooldownMs;
        public double djStaminaCost;
        public boolean djUsePercentageStamina;
        public double djStaminaLossPercentage;
        public boolean infiniteDoubleJump;
        /** Bound to {@code @djMaxJumps} in UI assets; persisted as {@link DoubleJumpConfig#jumpCharges}. */
        public double djJumpCharges;

        public AdminEventData() {}
    }

    public static final class AdminPage extends InteractiveCustomUIPage<AdminEventData> {

        public AdminPage(@Nonnull PlayerRef playerRef) {
            super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, AdminEventData.CODEC);
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
            commands.set("#DJMaxJumpsInput.Value", config.totalJumpCharges());
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
            @Nonnull AdminEventData data
        ) {
            DoubleJumpConfig config = DoubleJumpConfig.get();
            config.horizontalBoost = (float) data.djHorizontalBoost;
            config.verticalBoost = (float) data.djVerticalBoost;
            config.cooldownMs = (long) data.djCooldownMs;
            config.staminaCost = (float) data.djStaminaCost;
            config.usePercentageStamina = data.djUsePercentageStamina;
            config.staminaLossPercentage = (float) data.djStaminaLossPercentage;
            config.infiniteDoubleJump = data.infiniteDoubleJump;
            config.jumpCharges = (int) data.djJumpCharges;
            config.maxJumps = Math.max(0, config.jumpCharges - 1);
            DoubleJumpConfig.saveToDisk();
            ((HytaleLogger.Api) LOGGER.atFine()).log("Double Jump: Settings saved and applied");
            this.close();
        }
    }
}
