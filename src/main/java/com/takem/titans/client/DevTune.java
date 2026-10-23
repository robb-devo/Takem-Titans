package com.takem.titans.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;

public final class DevTune {
    private static final Properties VALUES = new Properties();
    private static long lastCheck;
    private static long lastModified = -1L;

    private DevTune() {
    }

    public static float get(String key, float def) {
        refresh();
        if (VALUES.isEmpty()) {
            return def;
        } else {
            String v = VALUES.getProperty(key);
            if (v == null) {
                return def;
            } else {
                try {
                    return Float.parseFloat(v.trim());
                } catch (NumberFormatException var4) {
                    return def;
                }
            }
        }
    }

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastCheck >= 500L) {
            lastCheck = now;
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("config/takemtitans-tune.properties");

            try {
                if (!Files.exists(file)) {
                    if (lastModified != -1L) {
                        VALUES.clear();
                        lastModified = -1L;
                    }

                    return;
                }

                long mod = Files.getLastModifiedTime(file).toMillis();
                if (mod == lastModified) {
                    return;
                }

                lastModified = mod;
                VALUES.clear();

                try (Reader r = Files.newBufferedReader(file)) {
                    VALUES.load(r);
                }
            } catch (IOException var10) {
            }
        }
    }
}
