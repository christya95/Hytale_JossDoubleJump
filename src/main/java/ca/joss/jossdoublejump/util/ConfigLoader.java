/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  com.hypixel.hytale.logger.HytaleLogger
 *  com.hypixel.hytale.logger.HytaleLogger$Api
 */
package ca.joss.jossdoublejump.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public final class ConfigLoader {
    private final HytaleLogger logger;

    public ConfigLoader(HytaleLogger logger) {
        this.logger = logger;
    }

    public <T> T load(Class<T> type, String configFileName, String defaultsResourceName, File dir, Gson gson) {
        File diskFile = new File(dir, configFileName);
        T defaults = this.loadDefaults(type, defaultsResourceName, gson);
        if (defaults == null) {
            ((HytaleLogger.Api)this.logger.atSevere()).log("'" + defaultsResourceName + "' missing from JAR \u2014 cannot load " + configFileName);
            return null;
        }
        if (!diskFile.exists()) {
            this.save(diskFile, defaults, gson);
            ((HytaleLogger.Api)this.logger.atInfo()).log("Created new " + configFileName);
            return defaults;
        }
        return this.loadAndMerge(type, diskFile, defaults, gson);
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private <T> T loadDefaults(Class<T> type, String resourceName, Gson gson) {
        try (InputStream is = type.getClassLoader().getResourceAsStream(resourceName);){
            if (is == null) {
                T t = null;
                return t;
            }
            Object object = gson.fromJson((Reader)new InputStreamReader(is), type);
            return (T)object;
        }
        catch (IOException e) {
            ((HytaleLogger.Api)((HytaleLogger.Api)this.logger.atSevere()).withCause((Throwable)e)).log("Failed to load defaults from " + resourceName);
            return null;
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private <T> T loadAndMerge(Class<T> type, File diskFile, T defaults, Gson gson) {
        try (FileReader reader = new FileReader(diskFile);){
            JsonObject userJson = JsonParser.parseReader((Reader)reader).getAsJsonObject();
            JsonObject defaultsJson = gson.toJsonTree(defaults).getAsJsonObject();
            boolean updated = false;
            for (String key : defaultsJson.keySet()) {
                if (userJson.has(key)) continue;
                userJson.add(key, defaultsJson.get(key));
                ((HytaleLogger.Api)this.logger.atInfo()).log("Added missing field: " + key);
                updated = true;
            }
            Object config = gson.fromJson((JsonElement)userJson, type);
            if (updated) {
                this.save(diskFile, config, gson);
            }
            Object object = config;
            return (T)object;
        }
        catch (IOException e) {
            ((HytaleLogger.Api)((HytaleLogger.Api)this.logger.atSevere()).withCause((Throwable)e)).log("Failed to read " + diskFile.getName() + "; using defaults");
            return defaults;
        }
    }

    public <T> void save(File file, T config, Gson gson) {
        try (FileWriter writer = new FileWriter(file);){
            gson.toJson(config, (Appendable)writer);
        }
        catch (IOException e) {
            ((HytaleLogger.Api)((HytaleLogger.Api)this.logger.atSevere()).withCause((Throwable)e)).log("Failed to save " + file.getName());
        }
    }
}

