package com.automation.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simple persistent cache for selector healing results.
 *
 * Stores: failedSelector -> healedSelector
 *
 * Default path: ./healing-cache.json (project working directory)
 */
@Slf4j
public class HealingCache {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final Path cachePath;
    private Map<String, String> cache = new HashMap<>();
    private boolean loaded = false;

    public HealingCache(Path cachePath) {
        this.cachePath = cachePath;
    }

    public synchronized Optional<String> get(String failedSelector) {
        ensureLoaded();
        return Optional.ofNullable(cache.get(failedSelector));
    }

    public synchronized void put(String failedSelector, String healedSelector) {
        if (failedSelector == null || healedSelector == null) {
            return;
        }
        ensureLoaded();
        cache.put(failedSelector, healedSelector);
        persistBestEffort();
    }

    public synchronized Map<String, String> snapshot() {
        ensureLoaded();
        return Collections.unmodifiableMap(new HashMap<>(cache));
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        if (cachePath == null) {
            return;
        }
        if (!Files.exists(cachePath)) {
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(cachePath);
            if (bytes.length == 0) {
                return;
            }
            Map<String, String> read = MAPPER.readValue(bytes, MAP_TYPE);
            if (read != null) {
                cache = new HashMap<>(read);
            }
        } catch (Exception e) {
            log.warn("Failed to read healing cache at {}. Starting with empty cache.", cachePath, e);
            cache = new HashMap<>();
        }
    }

    private void persistBestEffort() {
        if (cachePath == null) {
            return;
        }

        try {
            Path parent = cachePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tmp = cachePath.resolveSibling(cachePath.getFileName().toString() + ".tmp");
            byte[] bytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(cache);
            Files.write(tmp, bytes);
            Files.move(tmp, cachePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // On Windows/older FS, ATOMIC_MOVE may fail; retry without it.
            try {
                Path parent = cachePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Path tmp = cachePath.resolveSibling(cachePath.getFileName().toString() + ".tmp");
                byte[] bytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(cache);
                Files.write(tmp, bytes);
                Files.move(tmp, cachePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                log.warn("Failed to persist healing cache at {}", cachePath, ex);
            }
        } catch (Exception e) {
            log.warn("Failed to persist healing cache at {}", cachePath, e);
        }
    }
}

