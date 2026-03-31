/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.hypixel.hytale.codec.Codec
 *  com.hypixel.hytale.codec.KeyedCodec
 *  com.hypixel.hytale.codec.builder.BuilderCodec
 *  com.hypixel.hytale.codec.builder.BuilderCodec$Builder
 *  com.hypixel.hytale.component.Ref
 *  com.hypixel.hytale.component.Store
 *  com.hypixel.hytale.logger.HytaleLogger
 *  com.hypixel.hytale.logger.HytaleLogger$Api
 *  com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
 *  com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
 *  com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
 *  com.hypixel.hytale.server.core.ui.builder.EventData
 *  com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
 *  com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
 *  com.hypixel.hytale.server.core.universe.PlayerRef
 *  com.hypixel.hytale.server.core.universe.world.storage.EntityStore
 *  javax.annotation.Nonnull
 */
package org.narwhals.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
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
import org.narwhals.plugin.DoubleJump;
import org.narwhals.plugin.DoubleJumpConfig;
import org.narwhals.plugin.DoubleJumpLogger;

public class DoubleJumpAdminPage
extends InteractiveCustomUIPage<PageEventData> {
    private static final HytaleLogger LOGGER = DoubleJumpLogger.get();

    public DoubleJumpAdminPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEventData.CODEC);
    }

    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Pages/DoubleJumpAdmin.ui");
        commands.set("#InfoLabel.Text", DoubleJump.getVersion() + " - Admin");
        DoubleJumpConfig config = DoubleJumpConfig.get();
        commands.set("#DJHorizontalBoostInput.Value", config.horizontalBoost);
        commands.set("#DJVerticalBoostInput.Value", config.verticalBoost);
        commands.set("#DJCooldownInput.Value", (float)config.cooldownMs);
        commands.set("#DJStaminaCostInput.Value", config.staminaCost);
        commands.set("#DJUsePercentageStaminaCheckBox.Value", config.usePercentageStamina);
        commands.set("#DJStaminaLossPercentageInput.Value", config.staminaLossPercentage);
        commands.set("#InfiniteDoubleJumpCheckBox.Value", config.infiniteDoubleJump);
        commands.set("#DJMaxJumpsInput.Value", config.maxJumps);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", new EventData().append("@djHorizontalBoost", "#DJHorizontalBoostInput.Value").append("@djVerticalBoost", "#DJVerticalBoostInput.Value").append("@djCooldownMs", "#DJCooldownInput.Value").append("@djStaminaCost", "#DJStaminaCostInput.Value").append("@djUsePercentageStamina", "#DJUsePercentageStaminaCheckBox.Value").append("@djStaminaLossPercentage", "#DJStaminaLossPercentageInput.Value").append("@infiniteDoubleJump", "#InfiniteDoubleJumpCheckBox.Value").append("@djMaxJumps", "#DJMaxJumpsInput.Value"));
    }

    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageEventData data) {
        DoubleJumpConfig config = DoubleJumpConfig.get();
        config.horizontalBoost = (float)data.djHorizontalBoost;
        config.verticalBoost = (float)data.djVerticalBoost;
        config.cooldownMs = (long)data.djCooldownMs;
        config.staminaCost = (float)data.djStaminaCost;
        config.usePercentageStamina = data.djUsePercentageStamina;
        config.staminaLossPercentage = (float)data.djStaminaLossPercentage;
        config.infiniteDoubleJump = data.infiniteDoubleJump;
        config.maxJumps = (int)data.djMaxJumps;
        DoubleJumpConfig.saveToDisk();
        ((HytaleLogger.Api)LOGGER.atFine()).log("Double Jump: Settings saved and applied");
        this.close();
    }

    public static class PageEventData {
        public static final BuilderCodec<PageEventData> CODEC = ((BuilderCodec.Builder)((BuilderCodec.Builder)((BuilderCodec.Builder)((BuilderCodec.Builder)((BuilderCodec.Builder)((BuilderCodec.Builder)((BuilderCodec.Builder)((BuilderCodec.Builder)BuilderCodec.builder(PageEventData.class, PageEventData::new).append(new KeyedCodec("@djHorizontalBoost", (Codec)Codec.DOUBLE), (d, v) -> {
            d.djHorizontalBoost = v;
        }, d -> d.djHorizontalBoost).add()).append(new KeyedCodec("@djVerticalBoost", (Codec)Codec.DOUBLE), (d, v) -> {
            d.djVerticalBoost = v;
        }, d -> d.djVerticalBoost).add()).append(new KeyedCodec("@djCooldownMs", (Codec)Codec.DOUBLE), (d, v) -> {
            d.djCooldownMs = v;
        }, d -> d.djCooldownMs).add()).append(new KeyedCodec("@djStaminaCost", (Codec)Codec.DOUBLE), (d, v) -> {
            d.djStaminaCost = v;
        }, d -> d.djStaminaCost).add()).append(new KeyedCodec("@djUsePercentageStamina", (Codec)Codec.BOOLEAN), (d, v) -> {
            d.djUsePercentageStamina = v;
        }, d -> d.djUsePercentageStamina).add()).append(new KeyedCodec("@djStaminaLossPercentage", (Codec)Codec.DOUBLE), (d, v) -> {
            d.djStaminaLossPercentage = v;
        }, d -> d.djStaminaLossPercentage).add()).append(new KeyedCodec("@infiniteDoubleJump", (Codec)Codec.BOOLEAN), (d, v) -> {
            d.infiniteDoubleJump = v;
        }, d -> d.infiniteDoubleJump).add()).append(new KeyedCodec("@djMaxJumps", (Codec)Codec.DOUBLE), (d, v) -> {
            d.djMaxJumps = v;
        }, d -> d.djMaxJumps).add()).build();
        public double djHorizontalBoost;
        public double djVerticalBoost;
        public double djCooldownMs;
        public double djStaminaCost;
        public boolean djUsePercentageStamina;
        public double djStaminaLossPercentage;
        public boolean infiniteDoubleJump;
        public double djMaxJumps;
    }
}

