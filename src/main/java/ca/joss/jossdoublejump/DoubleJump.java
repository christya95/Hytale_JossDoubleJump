package ca.joss.jossdoublejump;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import ca.joss.jossdoublejump.util.ConfigLoader;

public class DoubleJump extends JavaPlugin {
    private static final HytaleLogger LOGGER = Log.INSTANCE;
    /** Writable folder; config file is {@link DoubleJumpConfig#CONFIG_FILE_NAME} inside it (e.g. {@code mods/JossDoubleJumpConfig.json}). */
    private static final File CONFIG_DIR = new File("mods");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConfigLoader CONFIG_LOADER = new ConfigLoader(LOGGER);
    private static ComponentType<EntityStore, DoubleJumpComponent> doubleJumpComponentType;
    private static String version;

    static {
        version = "JossDoubleJump";
    }

    public static ConfigLoader getConfigLoader() {
        return CONFIG_LOADER;
    }

    public DoubleJump(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static ComponentType<EntityStore, DoubleJumpComponent> getDoubleJumpComponentType() {
        return doubleJumpComponentType;
    }

    public static String getVersion() {
        return version;
    }

    @Override
    protected void setup() {
        ((HytaleLogger.Api) LOGGER.atInfo()).log("Setting Up");
        version = "v" + this.getManifest().getVersion().toString();
        ensureConfigDir();
        migrateLegacyConfigsIfNeeded();
        DoubleJumpConfig.load(CONFIG_DIR, GSON);
        ComponentRegistryProxy reg = this.getEntityStoreRegistry();
        doubleJumpComponentType = reg.registerComponent(DoubleJumpComponent.class, DoubleJumpComponent::new);
        this.getCodecRegistry(Interaction.CODEC).register("Double_Jump", DoubleJumpInteraction.class, DoubleJumpInteraction.CODEC);
        ((HytaleLogger.Api) LOGGER.atInfo()).log("Interaction registered (Double_Jump)");
        reg.registerSystem((ISystem) new PlayerJoinDoubleJumpAdder(doubleJumpComponentType));
        reg.registerSystem((ISystem) new DoubleJumpTicking.QueueScannerSystem(doubleJumpComponentType));
        reg.registerSystem((ISystem) new DoubleJumpTicking.AfterInputSystem(doubleJumpComponentType));
        this.getCommandRegistry().registerCommand((AbstractCommand) new DoubleJumpCommands.Mod());
        this.getCommandRegistry().registerCommand((AbstractCommand) new DoubleJumpCommands.Settings());
        if (DoubleJumpConfig.ActivationMode.from(DoubleJumpConfig.get()).usesAbilityInjection()) {
            GlobalAbilityUnlocker.inject();
        }
        ((HytaleLogger.Api) LOGGER.atInfo()).log("Setup Successful");
    }

    @Override
    protected void shutdown() {}

    private void ensureConfigDir() {
        if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs()) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("Failed to create mods directory");
        }
    }

    /**
     * Prefer {@code mods/JossDoubleJumpConfig.json}: copy once from older filenames/locations if the new file is missing.
     */
    private void migrateLegacyConfigsIfNeeded() {
        File primary = new File(CONFIG_DIR, DoubleJumpConfig.CONFIG_FILE_NAME);
        if (primary.exists()) {
            return;
        }
        File oldFlat = new File(CONFIG_DIR, "double_jump_config.json");
        File legacyNested = new File("mods/JossDoubleJump/double_jump_config.json");
        File source = oldFlat.isFile() ? oldFlat : (legacyNested.isFile() ? legacyNested : null);
        if (source == null) {
            return;
        }
        try {
            Files.copy(source.toPath(), primary.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ((HytaleLogger.Api) LOGGER.atInfo())
                .log("Migrated config %s -> mods/%s", source.getPath(), DoubleJumpConfig.CONFIG_FILE_NAME);
        } catch (IOException e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("Could not migrate legacy config: " + e.getMessage());
        }
    }
}

final class Log {
    static final HytaleLogger INSTANCE = HytaleLogger.get("DoubleJump");

    private Log() {}
}

final class PlayerJoinDoubleJumpAdder extends RefSystem<EntityStore> {
    private static final HytaleLogger LOGGER = Log.INSTANCE;
    private final ComponentType<EntityStore, DoubleJumpComponent> doubleJumpComponentType;

    PlayerJoinDoubleJumpAdder(ComponentType<EntityStore, DoubleJumpComponent> componentType) {
        this.doubleJumpComponentType = componentType;
    }

    @Nonnull
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    public void onEntityAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason addReason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (commandBuffer.getComponent(ref, this.doubleJumpComponentType) == null) {
            commandBuffer.addComponent(ref, this.doubleJumpComponentType, new DoubleJumpComponent());
            ((HytaleLogger.Api) LOGGER.atFine()).log("Added DoubleJumpComponent to player");
        } else {
            ((HytaleLogger.Api) LOGGER.atFine()).log("Resetting state for world transfer");
            DoubleJumpComponent doubleJumpComponent =
                (DoubleJumpComponent) commandBuffer.getComponent(ref, this.doubleJumpComponentType);
            if (doubleJumpComponent != null) {
                doubleJumpComponent.jumpCount = 0;
                doubleJumpComponent.lastDoubleJumpTimeMs = 0L;
                doubleJumpComponent.phase = DoubleJumpComponent.Phase.GROUNDED;
                doubleJumpComponent.inputState = DoubleJumpComponent.InputState.WAITING_FOR_PRESS;
                doubleJumpComponent.rawSignalLast = false;
                doubleJumpComponent.ticksWaitingForSecondJump = 0;
                doubleJumpComponent.sawSignalLowWhileWaiting = false;
                doubleJumpComponent.tapAssistConsumedThisAirborne = false;
                doubleJumpComponent.inputCooldownFramesRemaining = 0;
                doubleJumpComponent.jumpHeldLastQueue = false;
                doubleJumpComponent.chargesRemaining = 0;
                doubleJumpComponent.pendingQueueJumpEdge = false;
                doubleJumpComponent.queueJumpEdgeBufferUntilMs = 0L;
            }
        }
    }

    public void onEntityRemove(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason removeReason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {}
}

final class GlobalAbilityUnlocker {
    private static final HytaleLogger LOGGER = Log.INSTANCE;
    private static final String ROOT_DOUBLE_JUMP = "Root_DoubleJump";

    private GlobalAbilityUnlocker() {}

    static void inject() {
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
