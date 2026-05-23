package com.thegamecellar.gameservice.util;

import com.thegamecellar.gameservice.model.entity.Genre;
import com.thegamecellar.gameservice.repository.GenreRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule-based engine that promotes tag / theme combinations to first-class derived genres.
 * Rules are loaded from {@code derived-genres.yaml} at startup. Rule names that collide with
 * existing IGDB-sourced genre rows cause a fail-fast {@link IllegalStateException} so the
 * mismatch surfaces during boot instead of silently corrupting the join table at write time.
 */
@Slf4j
@Component
public class DerivedGenreEngine {

    private static final String RESOURCE_PATH = "derived-genres.yaml";
    private static final String IGDB_SOURCE = "IGDB";

    private final GenreRepository genreRepository;

    private List<DerivedRule> rules = List.of();
    private Set<String> derivedNames = Set.of();

    public DerivedGenreEngine(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("Derived-genre rules not found at classpath:{}. Engine disabled, no genres will be derived.",
                    RESOURCE_PATH);
            return;
        }

        List<DerivedRule> parsed = parseYaml(resource);
        if (parsed.isEmpty()) {
            log.warn("Derived-genre rules file at classpath:{} contains zero rules. Engine disabled.", RESOURCE_PATH);
            return;
        }

        validateNoIgdbCollisions(parsed);

        this.rules = List.copyOf(parsed);
        this.derivedNames = parsed.stream().map(DerivedRule::name).collect(Collectors.toUnmodifiableSet());
        log.info("Derived-genre engine loaded: {} rules ({})",
                rules.size(),
                rules.stream().map(DerivedRule::name).collect(Collectors.joining(", ")));
    }

    /**
     * Returns the set of derived genre names that match the given tag + theme inputs. Inputs
     * are normalized through {@link CuratedTagAllowlist#normalize(String)} before matching so
     * surface variants ("Souls-Like" / "souls like" / "souls likes") collapse to one key.
     */
    public Set<String> deriveGenres(Set<String> tagNames, Set<String> themeNames) {
        if (rules.isEmpty()) return Set.of();
        Set<String> normalizedTags = normalizeAll(tagNames);
        Set<String> normalizedThemes = normalizeAll(themeNames);

        Set<String> result = new HashSet<>();
        for (DerivedRule rule : rules) {
            if (rule.matches(normalizedTags, normalizedThemes)) {
                result.add(rule.name());
            }
        }
        return result;
    }

    /** True when the supplied genre name is governed by a derived-genre rule (not IGDB). */
    public boolean isDerivedName(String genreName) {
        return derivedNames.contains(genreName);
    }

    public Set<String> getRuleNames() {
        return derivedNames;
    }

    public int ruleCount() {
        return rules.size();
    }

    // ── parsing ───────────────────────────────────────────────────────────────

    private List<DerivedRule> parseYaml(ClassPathResource resource) {
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (InputStream in = resource.getInputStream()) {
            root = yaml.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE_PATH, e);
        }
        if (root == null || root.isEmpty()) {
            return List.of();
        }

        List<DerivedRule> list = new ArrayList<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            if (name.isEmpty()) {
                throw new IllegalStateException(RESOURCE_PATH + " contains a rule with an empty name");
            }
            if (!(entry.getValue() instanceof Map<?, ?> ruleMap)) {
                throw new IllegalStateException("Rule '" + name + "' must be a map of match blocks");
            }
            Set<String> tagsAny = readNormalizedList(ruleMap, "tagsAny", name);
            Set<String> tagsAll = readNormalizedList(ruleMap, "tagsAll", name);
            Set<String> themesAny = readNormalizedList(ruleMap, "themesAny", name);
            Set<String> themesAll = readNormalizedList(ruleMap, "themesAll", name);

            if (tagsAny.isEmpty() && tagsAll.isEmpty() && themesAny.isEmpty() && themesAll.isEmpty()) {
                throw new IllegalStateException("Rule '" + name + "' has no match conditions");
            }
            list.add(new DerivedRule(name, tagsAny, tagsAll, themesAny, themesAll));
        }
        return list;
    }

    private Set<String> readNormalizedList(Map<?, ?> ruleMap, String key, String ruleName) {
        Object value = ruleMap.get(key);
        if (value == null) return Set.of();
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("Rule '" + ruleName + "': '" + key + "' must be a YAML list");
        }
        Set<String> result = new HashSet<>();
        for (Object item : list) {
            if (item == null) continue;
            String norm = CuratedTagAllowlist.normalize(item.toString());
            if (!norm.isEmpty()) result.add(norm);
        }
        return Set.copyOf(result);
    }

    private void validateNoIgdbCollisions(List<DerivedRule> parsed) {
        List<String> collisions = new ArrayList<>();
        for (DerivedRule rule : parsed) {
            Optional<Genre> existing = genreRepository.findByName(rule.name());
            if (existing.isPresent() && IGDB_SOURCE.equals(existing.get().getSource())) {
                collisions.add(rule.name());
            }
        }
        if (!collisions.isEmpty()) {
            throw new IllegalStateException(
                    "Derived-genre rule names collide with existing IGDB genres: " + collisions
                            + ". Rename these rules in " + RESOURCE_PATH + " before starting the service."
            );
        }
    }

    private static Set<String> normalizeAll(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        Set<String> result = new HashSet<>(values.size() * 2);
        for (String v : values) {
            String norm = CuratedTagAllowlist.normalize(v);
            if (!norm.isEmpty()) result.add(norm);
        }
        return result;
    }

    // ── rule record ───────────────────────────────────────────────────────────

    /** Match blocks combine with OR; *All blocks require every entry to be present. */
    record DerivedRule(String name,
                       Set<String> tagsAny,
                       Set<String> tagsAll,
                       Set<String> themesAny,
                       Set<String> themesAll) {
        boolean matches(Set<String> tags, Set<String> themes) {
            if (!tagsAny.isEmpty() && !Collections.disjoint(tags, tagsAny)) return true;
            if (!tagsAll.isEmpty() && tags.containsAll(tagsAll)) return true;
            if (!themesAny.isEmpty() && !Collections.disjoint(themes, themesAny)) return true;
            if (!themesAll.isEmpty() && themes.containsAll(themesAll)) return true;
            return false;
        }
    }
}
