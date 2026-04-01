package org.narwhals.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.File;
import javax.annotation.Nonnull;
import org.narwhals.HytaleModBase.ConfigLoader;

public class DoubleJump extends JavaPlugin {
    private static final HytaleLogger LOGGER = DoubleJumpLogger.get();
    private static final File CONFIG_DIR = new File("mods/JossDoubleJump");
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
        DoubleJumpConfig.load(CONFIG_DIR, GSON);
        ComponentRegistryProxy reg = this.getEntityStoreRegistry();
        doubleJumpComponentType = reg.registerComponent(DoubleJumpComponent.class, DoubleJumpComponent::new);
        this.getCodecRegistry(Interaction.CODEC).register("Double_Jump", DoubleJumpInteraction.class, DoubleJumpInteraction.CODEC);
        ((HytaleLogger.Api) LOGGER.atInfo()).log("Interaction registered (Double_Jump)");
        reg.registerSystem((ISystem) new PlayerJoinDoubleJumpAdder(doubleJumpComponentType));
        reg.registerSystem((ISystem) new DoubleJumpQueueScannerSystem(doubleJumpComponentType));
        reg.registerSystem((ISystem) new DoubleJumpSystem(doubleJumpComponentType));
        this.getCommandRegistry().registerCommand((AbstractCommand) new DoubleJumpModCommand());
        this.getCommandRegistry().registerCommand((AbstractCommand) new DoubleJumpSettingsCommand());
        if (ActivationMode.from(DoubleJumpConfig.get()).usesAbilityInjection()) {
            GlobalAbilityUnlocker.inject();
        }
        ((HytaleLogger.Api) LOGGER.atInfo()).log("Setup Successful");
    }

    @Override
    protected void shutdown() {}

    private void ensureConfigDir() {
        if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs()) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("Failed to create config directory");
        }
    }
}
