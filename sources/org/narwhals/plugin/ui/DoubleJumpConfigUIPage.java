package org.narwhals.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.narwhals.plugin.ActivationMode;
import org.narwhals.plugin.DoubleJump;
import org.narwhals.plugin.DoubleJumpConfig;

public class DoubleJumpConfigUIPage extends CustomUIPage {

    private static final String NA = "N/A";

    public DoubleJumpConfigUIPage(@Nonnull PlayerRef player) {
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
            c.set("#DJMaxJumpsValue.Text", NA);
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
        c.set("#DJMaxJumpsValue.Text", cfg.infiniteDoubleJump ? "Unlimited" : String.valueOf(cfg.maxJumps));
        c.set("#ActivationMethodValue.Text", ActivationMode.from(cfg).uiLabel());
    }
}
