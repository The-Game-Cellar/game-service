package com.thegamecellar.gameservice.util;

import java.util.Map;

// IGDB v4 categories: 1=ESRB, 2=PEGI, 3=CERO, 4=USK, 5=GRAC, 6=CLASS_IND, 7=ACB.
// Only ESRB + PEGI mapped; others return null so the frontend skips them in display priority.
public final class AgeRatingMapper {

    private static final int CATEGORY_ESRB = 1;
    private static final int CATEGORY_PEGI = 2;

    private static final Map<Integer, String> ESRB_LABELS = Map.of(
            6, "RP",
            7, "EC",
            8, "E",
            9, "E10",
            10, "T",
            11, "M",
            12, "AO"
    );

    private static final Map<Integer, String> PEGI_LABELS = Map.of(
            1, "3",
            2, "7",
            3, "12",
            4, "16",
            5, "18"
    );

    private AgeRatingMapper() {}

    public static String body(Integer category) {
        if (category == null) return null;
        return switch (category) {
            case CATEGORY_ESRB -> "ESRB";
            case CATEGORY_PEGI -> "PEGI";
            default -> null;
        };
    }

    public static String label(Integer category, Integer rating) {
        if (category == null || rating == null) return null;
        return switch (category) {
            case CATEGORY_ESRB -> ESRB_LABELS.get(rating);
            case CATEGORY_PEGI -> PEGI_LABELS.get(rating);
            default -> null;
        };
    }
}
