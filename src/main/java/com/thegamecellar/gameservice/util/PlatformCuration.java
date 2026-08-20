package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.entity.Platform;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Loads platform-curation.yaml, the source of truth for which platforms reach the Preferences picker.
// Authoritative in both directions: a platform dropped from the file is reset to the uncurated defaults.
@Slf4j
@Component
public class PlatformCuration {

    static final String RESOURCE_PATH = "platform-curation.yaml";
    private static final int UNCURATED_DISPLAY_ORDER = 999;

    private Map<String, Entry> byName = Map.of();
    private boolean enabled = false;

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("Platform curation not found at classpath:{}. No platform reaches the Preferences picker, "
                    + "so onboarding and platform preferences stay empty. Add the file to enable curation.",
                    RESOURCE_PATH);
            return;
        }

        Map<String, Entry> parsed = parseYaml(resource);
        if (parsed.isEmpty()) {
            log.warn("Platform curation file at classpath:{} contains zero entries. Curation disabled.", RESOURCE_PATH);
            return;
        }

        this.byName = Map.copyOf(parsed);
        this.enabled = true;
        log.info("Platform curation loaded: {} platforms across {} categories.", byName.size(), categoryCount());
    }

    // Writes the file's verdict onto the entity without saving. Returns true when a field actually changed,
    // so the reconciler can skip untouched rows. A platform absent from the file is reset to the defaults.
    public boolean apply(Platform platform) {
        if (!enabled || platform == null) return false;
        Entry entry = byName.get(platform.getName());

        boolean eligible = entry != null;
        String category = entry == null ? null : entry.category();
        int displayOrder = entry == null ? UNCURATED_DISPLAY_ORDER : entry.order();

        if (Objects.equals(platform.getIsPreferenceEligible(), eligible)
                && Objects.equals(platform.getCategory(), category)
                && Objects.equals(platform.getDisplayOrder(), displayOrder)) {
            return false;
        }

        platform.setIsPreferenceEligible(eligible);
        platform.setCategory(category);
        platform.setDisplayOrder(displayOrder);
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int size() {
        return byName.size();
    }

    public Set<String> curatedNames() {
        return byName.keySet();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Entry> parseYaml(ClassPathResource resource) {
        Map<String, Object> root;
        try (InputStream in = resource.getInputStream()) {
            root = new Yaml().load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE_PATH, e);
        }
        if (root == null || root.isEmpty()) {
            return Map.of();
        }

        Map<String, Entry> result = new LinkedHashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, Object> group : root.entrySet()) {
            String category = group.getKey() == null ? "" : group.getKey().trim();
            if (category.isEmpty()) {
                throw new IllegalStateException(RESOURCE_PATH + " contains a category with an empty name");
            }
            if (!(group.getValue() instanceof List<?> entries)) {
                throw new IllegalStateException("Category '" + category + "' must be a YAML list of platforms");
            }
            for (Object item : entries) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new IllegalStateException("Category '" + category + "' contains a non-map entry: " + item);
                }
                String name = readName((Map<String, Object>) map, category);
                Integer order = readOrder((Map<String, Object>) map, name);
                Entry previous = result.put(name, new Entry(category, order));
                if (previous != null) duplicates.add(name);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException(
                    "Platform names appear in more than one category in " + RESOURCE_PATH + ": " + duplicates
                            + ". Each platform belongs to exactly one category."
            );
        }
        return result;
    }

    private String readName(Map<String, Object> map, String category) {
        Object raw = map.get("name");
        String name = raw == null ? "" : raw.toString().trim();
        if (name.isEmpty()) {
            throw new IllegalStateException("Category '" + category + "' contains an entry with no 'name'");
        }
        return name;
    }

    private Integer readOrder(Map<String, Object> map, String name) {
        Object raw = map.get("order");
        if (raw == null) {
            throw new IllegalStateException("Platform '" + name + "' has no 'order' in " + RESOURCE_PATH);
        }
        if (!(raw instanceof Integer order)) {
            throw new IllegalStateException("Platform '" + name + "': 'order' must be an integer, got: " + raw);
        }
        return order;
    }

    private long categoryCount() {
        return byName.values().stream().map(Entry::category).distinct().count();
    }

    record Entry(String category, int order) {}
}
