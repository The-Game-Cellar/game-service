package com.thegamecellar.gameservice.util;

import java.util.*;

public class MoodMapper {

    private static final Map<String, List<String>> TAG_TO_MOODS = new HashMap<>();
    private static final Map<String, List<String>> GENRE_TO_MOODS = new HashMap<>();

    static {
        // Story-driven
        TAG_TO_MOODS.put("story rich",            List.of("Story-driven"));
        TAG_TO_MOODS.put("choices matter",        List.of("Story-driven", "Emotional"));
        TAG_TO_MOODS.put("interactive fiction",   List.of("Story-driven"));
        TAG_TO_MOODS.put("visual novel",          List.of("Story-driven"));
        TAG_TO_MOODS.put("narrative",             List.of("Story-driven"));

        // Atmospheric
        TAG_TO_MOODS.put("atmospheric",           List.of("Atmospheric"));
        TAG_TO_MOODS.put("cinematic",             List.of("Atmospheric"));
        TAG_TO_MOODS.put("immersive sim",         List.of("Atmospheric"));
        TAG_TO_MOODS.put("beautiful",             List.of("Atmospheric"));

        // Exploration
        TAG_TO_MOODS.put("exploration",           List.of("Exploration"));
        TAG_TO_MOODS.put("open world",            List.of("Exploration", "Epic"));
        TAG_TO_MOODS.put("sandbox",               List.of("Exploration", "Creative"));
        TAG_TO_MOODS.put("walking simulator",     List.of("Exploration", "Emotional"));

        // Intense
        TAG_TO_MOODS.put("difficult",             List.of("Intense"));
        TAG_TO_MOODS.put("gore",                  List.of("Intense", "Dark/Gritty"));
        TAG_TO_MOODS.put("violent",               List.of("Intense"));
        TAG_TO_MOODS.put("fast-paced",            List.of("Intense", "Fast-paced"));
        TAG_TO_MOODS.put("souls-like",            List.of("Intense", "Dark/Gritty"));
        TAG_TO_MOODS.put("hack and slash",        List.of("Intense", "Fast-paced"));
        TAG_TO_MOODS.put("bullet hell",           List.of("Intense", "Fast-paced"));

        // Competitive
        TAG_TO_MOODS.put("multiplayer",           List.of("Competitive"));
        TAG_TO_MOODS.put("online multiplayer",    List.of("Competitive"));
        TAG_TO_MOODS.put("pvp",                   List.of("Competitive"));
        TAG_TO_MOODS.put("esports",               List.of("Competitive"));
        TAG_TO_MOODS.put("competitive",           List.of("Competitive"));

        // Chill
        TAG_TO_MOODS.put("relaxing",              List.of("Chill", "Meditative"));
        TAG_TO_MOODS.put("casual",                List.of("Chill"));
        TAG_TO_MOODS.put("peaceful",              List.of("Chill"));
        TAG_TO_MOODS.put("idler",                 List.of("Chill"));

        // Cozy
        TAG_TO_MOODS.put("cozy",                  List.of("Cozy", "Chill"));
        TAG_TO_MOODS.put("farming",               List.of("Cozy"));
        TAG_TO_MOODS.put("life sim",              List.of("Cozy"));
        TAG_TO_MOODS.put("cute",                  List.of("Cozy"));
        TAG_TO_MOODS.put("family friendly",       List.of("Cozy"));

        // Tactical
        TAG_TO_MOODS.put("stealth",               List.of("Tactical"));
        TAG_TO_MOODS.put("turn-based",            List.of("Tactical"));
        TAG_TO_MOODS.put("turn-based strategy",   List.of("Tactical"));
        TAG_TO_MOODS.put("tactical",              List.of("Tactical"));
        TAG_TO_MOODS.put("real time tactics",     List.of("Tactical"));

        // Creative
        TAG_TO_MOODS.put("building",              List.of("Creative"));
        TAG_TO_MOODS.put("base building",         List.of("Creative", "Survival"));
        TAG_TO_MOODS.put("crafting",              List.of("Creative"));
        TAG_TO_MOODS.put("level editor",          List.of("Creative"));
        TAG_TO_MOODS.put("moddable",              List.of("Creative"));

        // Social
        TAG_TO_MOODS.put("co-op",                 List.of("Social"));
        TAG_TO_MOODS.put("cooperative",           List.of("Social"));
        TAG_TO_MOODS.put("local co-op",           List.of("Social"));
        TAG_TO_MOODS.put("online co-op",          List.of("Social"));
        TAG_TO_MOODS.put("split screen",          List.of("Social"));
        TAG_TO_MOODS.put("party",                 List.of("Social"));

        // Spooky
        TAG_TO_MOODS.put("horror",                List.of("Spooky"));
        TAG_TO_MOODS.put("survival horror",       List.of("Spooky", "Intense"));
        TAG_TO_MOODS.put("psychological horror",  List.of("Spooky", "Atmospheric"));
        TAG_TO_MOODS.put("zombies",               List.of("Spooky"));

        // Fast-paced
        TAG_TO_MOODS.put("fps",                   List.of("Fast-paced"));
        TAG_TO_MOODS.put("shoot 'em up",          List.of("Fast-paced"));
        TAG_TO_MOODS.put("arcade",                List.of("Fast-paced", "Nostalgic"));
        TAG_TO_MOODS.put("twitch",                List.of("Fast-paced"));

        // Epic
        TAG_TO_MOODS.put("fantasy",               List.of("Epic"));
        TAG_TO_MOODS.put("sci-fi",                List.of("Epic"));
        TAG_TO_MOODS.put("grand strategy",        List.of("Epic", "Tactical"));
        TAG_TO_MOODS.put("lore-rich",             List.of("Epic", "Story-driven"));

        // Emotional
        TAG_TO_MOODS.put("emotional",             List.of("Emotional"));
        TAG_TO_MOODS.put("drama",                 List.of("Emotional"));

        // Humorous
        TAG_TO_MOODS.put("funny",                 List.of("Humorous"));
        TAG_TO_MOODS.put("comedy",                List.of("Humorous"));
        TAG_TO_MOODS.put("satire",                List.of("Humorous"));
        TAG_TO_MOODS.put("parody",                List.of("Humorous"));

        // Mystery
        TAG_TO_MOODS.put("mystery",               List.of("Mystery"));
        TAG_TO_MOODS.put("detective",             List.of("Mystery"));
        TAG_TO_MOODS.put("investigation",         List.of("Mystery"));

        // Survival
        TAG_TO_MOODS.put("survival",              List.of("Survival"));
        TAG_TO_MOODS.put("post-apocalyptic",      List.of("Survival", "Dark/Gritty"));
        TAG_TO_MOODS.put("roguelike",             List.of("Survival"));
        TAG_TO_MOODS.put("roguelite",             List.of("Survival"));

        // Nostalgic
        TAG_TO_MOODS.put("pixel graphics",        List.of("Nostalgic"));
        TAG_TO_MOODS.put("retro",                 List.of("Nostalgic"));
        TAG_TO_MOODS.put("classic",               List.of("Nostalgic"));
        TAG_TO_MOODS.put("8-bit",                 List.of("Nostalgic"));
        TAG_TO_MOODS.put("16-bit",                List.of("Nostalgic"));

        // Meditative
        TAG_TO_MOODS.put("minimalist",            List.of("Meditative"));
        TAG_TO_MOODS.put("abstract",              List.of("Meditative"));
        TAG_TO_MOODS.put("ambient",               List.of("Meditative"));
        TAG_TO_MOODS.put("great soundtrack",      List.of("Meditative"));

        // Dark/Gritty
        TAG_TO_MOODS.put("dark",                  List.of("Dark/Gritty"));
        TAG_TO_MOODS.put("dark fantasy",          List.of("Dark/Gritty", "Epic"));
        TAG_TO_MOODS.put("mature",                List.of("Dark/Gritty"));
        TAG_TO_MOODS.put("noir",                  List.of("Dark/Gritty", "Mystery"));

        // Genre fallbacks
        GENRE_TO_MOODS.put("Action",                List.of("Intense", "Fast-paced"));
        GENRE_TO_MOODS.put("RPG",                   List.of("Story-driven", "Exploration", "Epic"));
        GENRE_TO_MOODS.put("Adventure",             List.of("Story-driven", "Exploration", "Atmospheric"));
        GENRE_TO_MOODS.put("Shooter",               List.of("Intense", "Fast-paced", "Competitive"));
        GENRE_TO_MOODS.put("Strategy",              List.of("Tactical", "Competitive"));
        GENRE_TO_MOODS.put("Simulation",            List.of("Chill", "Cozy", "Creative"));
        GENRE_TO_MOODS.put("Puzzle",                List.of("Chill", "Meditative"));
        GENRE_TO_MOODS.put("Platformer",            List.of("Fast-paced", "Nostalgic"));
        GENRE_TO_MOODS.put("Racing",                List.of("Fast-paced", "Competitive"));
        GENRE_TO_MOODS.put("Sports",                List.of("Competitive", "Social"));
        GENRE_TO_MOODS.put("Fighting",              List.of("Intense", "Competitive"));
        GENRE_TO_MOODS.put("Indie",                 List.of("Atmospheric", "Creative"));
        GENRE_TO_MOODS.put("Casual",                List.of("Chill", "Humorous"));
        GENRE_TO_MOODS.put("Arcade",                List.of("Fast-paced", "Nostalgic"));
        GENRE_TO_MOODS.put("Massively Multiplayer", List.of("Social", "Epic", "Competitive"));
        GENRE_TO_MOODS.put("Family",                List.of("Cozy", "Humorous", "Social"));
        GENRE_TO_MOODS.put("Board Games",           List.of("Chill", "Tactical", "Social"));
        GENRE_TO_MOODS.put("Card",                  List.of("Tactical", "Chill"));
        GENRE_TO_MOODS.put("Educational",           List.of("Meditative", "Creative"));
    }

    public static List<String> getTagsForMood(String mood) {
        return TAG_TO_MOODS.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(m -> m.equalsIgnoreCase(mood)))
                .map(Map.Entry::getKey)
                .toList();
    }

    public static List<String> getAllMoods() {
        return TAG_TO_MOODS.values().stream()
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> getMoods(List<String> tags, List<String> genres) {
        Set<String> moods = new LinkedHashSet<>();

        if (tags != null) {
            for (String tag : tags) {
                List<String> mapped = TAG_TO_MOODS.get(tag.toLowerCase());
                if (mapped != null) {
                    moods.addAll(mapped);
                }
            }
        }

        if (moods.isEmpty() && genres != null) {
            for (String genre : genres) {
                List<String> mapped = GENRE_TO_MOODS.get(genre);
                if (mapped != null) {
                    moods.addAll(mapped);
                }
            }
        }

        return new ArrayList<>(moods);
    }
}
