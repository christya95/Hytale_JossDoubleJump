package org.narwhals.plugin;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class GlobalAbilityUnlocker {
    private static final HytaleLogger LOGGER = DoubleJumpLogger.get();
    private static final String ROOT_DOUBLE_JUMP = "Root_DoubleJump";

    private GlobalAbilityUnlocker() {}

    public static void inject() {
        if (Item.getAssetMap() == null || Item.getAssetMap().getAssetMap().isEmpty()) {
            ((HytaleLogger.Api) LOGGER.atInfo()).log("Waiting for Items to load");
            HytaleServer.get().getEventBus().register(LoadedAssetsEvent.class, Item.class, GlobalAbilityUnlocker::onItemsLoaded);
        } else {
            injectNow();
        }
    }

    private static void onItemsLoaded(LoadedAssetsEvent<String, Item, ?> e) {
        injectNow();
    }

    private static Set<InteractionType> abilitySlots(DoubleJumpConfig cfg) {
        Set<InteractionType> s = EnumSet.noneOf(InteractionType.class);
        if (cfg == null) {
            return s;
        }
        if (cfg.useAbility2) {
            s.add(InteractionType.Ability2);
        }
        if (cfg.useAbility3) {
            s.add(InteractionType.Ability3);
        }
        return s;
    }

    private static void injectNow() {
        Set<InteractionType> slots = abilitySlots(DoubleJumpConfig.get());
        if (slots.isEmpty()) {
            ((HytaleLogger.Api) LOGGER.atInfo()).log("Neither Ability2 nor Ability3 enabled, skipping injection");
            return;
        }

        ((HytaleLogger.Api) LOGGER.atInfo())
            .log(
                "Injecting %s - Ability2: %b, Ability3: %b",
                (Object) ROOT_DOUBLE_JUMP,
                (Object) slots.contains(InteractionType.Ability2),
                (Object) slots.contains(InteractionType.Ability3));

        int n = 0;
        try {
            Field interactions = Item.class.getDeclaredField("interactions");
            interactions.setAccessible(true);
            Field cachedPacket = Item.class.getDeclaredField("cachedPacket");
            cachedPacket.setAccessible(true);
            if (Item.getAssetMap() == null) {
                return;
            }
            for (Item item : Item.getAssetMap().getAssetMap().values()) {
                try {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Map raw = item.getInteractions();
                    EnumMap<InteractionType, String> next = new EnumMap<>(InteractionType.class);
                    next.putAll(raw);
                    boolean changed = false;
                    for (InteractionType slot : slots) {
                        if (!next.containsKey(slot)) {
                            next.put(slot, ROOT_DOUBLE_JUMP);
                            changed = true;
                        }
                    }
                    if (changed) {
                        interactions.set(item, Collections.unmodifiableMap(next));
                        cachedPacket.set(item, null);
                        n++;
                    }
                } catch (Exception ex) {
                    ((HytaleLogger.Api) LOGGER.atWarning()).log("Exception: " + ex);
                }
            }
        } catch (Exception ex) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("Injection Failed:" + ex);
        }
        ((HytaleLogger.Api) LOGGER.atInfo()).log("Injected %s on %d items.", ROOT_DOUBLE_JUMP, n);
    }
}
