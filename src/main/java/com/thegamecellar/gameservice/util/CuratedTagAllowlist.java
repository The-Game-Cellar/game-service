package com.thegamecellar.gameservice.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class CuratedTagAllowlist {

    private static final String RESOURCE_PATH = "curated-tags.txt";

    private Set<String> normalizedAllowed = Set.of();
    private boolean enabled = false;

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("Curated tag allowlist not found at classpath:{}. Every tag will be accepted (filter disabled). " +
                    "Add the file to enforce the allowlist.", RESOURCE_PATH);
            return;
        }
        Set<String> entries = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String key = normalize(trimmed);
                if (!key.isEmpty()) entries.add(key);
            }
        } catch (Exception e) {
            log.error("Failed to load curated tag allowlist from classpath:{}. Filter disabled. Error: {}",
                    RESOURCE_PATH, e.getMessage());
            return;
        }
        if (entries.isEmpty()) {
            log.warn("Curated tag allowlist file at classpath:{} contains zero entries. Filter disabled.", RESOURCE_PATH);
            return;
        }
        this.normalizedAllowed = Set.copyOf(entries);
        this.enabled = true;
        log.info("Curated tag allowlist loaded: {} unique normalized entries enforced.", entries.size());
    }

    // Returns true unconditionally when the allowlist is disabled (missing or empty resource file).
    public boolean isAllowed(String rawTag) {
        if (!enabled) return true;
        if (rawTag == null) return false;
        String key = normalize(rawTag);
        if (key.isEmpty()) return false;
        return normalizedAllowed.contains(key);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int size() {
        return normalizedAllowed.size();
    }

    // Collapses surface variants ("Souls-Like" / "souls like" / "souls likes") to one key.
    // Trailing "s" stripped only when bare token > 4 chars so "rpgs" → "rpg" but "boss" stays "boss".
    static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) return "";
        s = s.replace('-', ' ');
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 4 && s.endsWith("s") && !s.endsWith("ss")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
