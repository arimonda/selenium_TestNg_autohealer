package com.automation.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates Page Object source code to persist healed selectors.
 *
 * Behavior (as requested):
 * - Do NOT delete the old selector line
 * - Comment the old @SeleniumSelector line
 * - Add a new @SeleniumSelector line below it with the healed selector
 *
 * This is best-effort and intentionally conservative:
 * - Only updates fields that look like: "@SeleniumSelector(...)" immediately above "private SmartElement <fieldName>;"
 */
@Slf4j
public final class SelectorCodeUpdater {
    private SelectorCodeUpdater() {}

    public static boolean updateAnnotatedSelector(Class<?> pageClass, String fieldName, String oldSelector, String newSelector) {
        if (pageClass == null || fieldName == null || fieldName.isBlank() || oldSelector == null || newSelector == null) {
            return false;
        }
        String trimmedNew = newSelector.trim();
        if (trimmedNew.isEmpty() || trimmedNew.equals(oldSelector.trim())) {
            return false;
        }

        Path javaFile = resolveSourceFile(pageClass);
        if (javaFile == null || !Files.exists(javaFile)) {
            log.warn("[AI-HEAL] Could not resolve source file for {}", pageClass.getName());
            return false;
        }

        try {
            String src = Files.readString(javaFile, StandardCharsets.UTF_8);
            String updated = patchSelectorForField(src, fieldName, oldSelector, trimmedNew);
            if (updated == null || updated.equals(src)) {
                return false;
            }

            backup(javaFile);
            Files.writeString(javaFile, updated, StandardCharsets.UTF_8);
            log.warn("[AI-HEAL] Updated selector in source: {}#{}", pageClass.getSimpleName(), fieldName);
            return true;
        } catch (Exception e) {
            log.warn("[AI-HEAL] Failed to update selector in source for {}#{}", pageClass.getSimpleName(), fieldName, e);
            return false;
        }
    }

    private static Path resolveSourceFile(Class<?> pageClass) {
        // Assumes standard Maven layout and that code runs from project root.
        // com.automation.pages.LoginPage -> src/main/java/com/automation/pages/LoginPage.java
        String rel = "src/main/java/" + pageClass.getName().replace('.', '/') + ".java";
        return Paths.get(rel);
    }

    private static String patchSelectorForField(String src, String fieldName, String oldSelector, String newSelector) {
        // Match:
        // <indent>@SeleniumSelector(value = "....")
        // <indent>private SmartElement <fieldName>;
        //
        // Allow whitespace variations, capture indentation for nice formatting.
        String pattern = "(?m)^(?<indent>\\s*)@SeleniumSelector\\s*\\(\\s*value\\s*=\\s*\"(?<sel>[^\"]*)\"\\s*\\)\\s*\\R" +
                "^(?<indent2>\\s*)private\\s+SmartElement\\s+" + Pattern.quote(fieldName) + "\\s*;";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(src);
        if (!m.find()) {
            return null;
        }

        String currentSel = m.group("sel");
        // Only update if we are still on the selector we think we are.
        // (If the file was already changed manually, we avoid stomping it.)
        if (!safeNormalize(currentSel).equals(safeNormalize(oldSelector))) {
            return null;
        }

        String indent = m.group("indent");
        String indent2 = m.group("indent2");
        String commentedOld = indent + "// @SeleniumSelector(value = \"" + escapeForJava(oldSelector.trim()) + "\")\n";
        String newLine = indent + "@SeleniumSelector(value = \"" + escapeForJava(newSelector.trim()) + "\")\n";
        String fieldLine = indent2 + "private SmartElement " + fieldName + ";";

        String replacement = commentedOld + newLine + fieldLine;
        return m.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static String safeNormalize(String s) {
        return (s == null) ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String escapeForJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void backup(Path javaFile) {
        try {
            Path dir = javaFile.getParent();
            if (dir == null) return;
            Path backups = dir.resolve(".selector_backups");
            Files.createDirectories(backups);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            Path out = backups.resolve(javaFile.getFileName().toString() + "." + ts + ".bak");
            Files.copy(javaFile, out);
        } catch (IOException ignored) {
            // best-effort only
        }
    }
}

