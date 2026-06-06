package com.fee.app.schoolfeeapp.school.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Standard Nigerian grade levels.
 * System-defined constants. Schools enable/disable which ones they use.
 */
public enum GradeLevel {

    NURSERY_1("Nursery 1", "NURSERY", 1),
    NURSERY_2("Nursery 2", "NURSERY", 2),
    NURSERY_3("Nursery 3", "NURSERY", 3),

    PRIMARY_1("Primary 1", "PRIMARY", 4),
    PRIMARY_2("Primary 2", "PRIMARY", 5),
    PRIMARY_3("Primary 3", "PRIMARY", 6),
    PRIMARY_4("Primary 4", "PRIMARY", 7),
    PRIMARY_5("Primary 5", "PRIMARY", 8),
    PRIMARY_6("Primary 6", "PRIMARY", 9),

    JSS_1("JSS 1", "JUNIOR_SECONDARY", 10),
    JSS_2("JSS 2", "JUNIOR_SECONDARY", 11),
    JSS_3("JSS 3", "JUNIOR_SECONDARY", 12),

    SSS_1("SSS 1", "SENIOR_SECONDARY", 13),
    SSS_2("SSS 2", "SENIOR_SECONDARY", 14),
    SSS_3("SSS 3", "SENIOR_SECONDARY", 15);

    private final String displayName;
    private final String category;
    private final int sortOrder;

    GradeLevel(String displayName, String category, int sortOrder) {
        this.displayName = displayName;
        this.category = category;
        this.sortOrder = sortOrder;
    }

    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
    public int getSortOrder() { return sortOrder; }

    /**
     * Get all grade levels ordered by sortOrder.
     */
    public static List<GradeLevel> getAllOrdered() {
        return Arrays.stream(values())
                .sorted(java.util.Comparator.comparingInt(GradeLevel::getSortOrder))
                .toList();
    }

    /**
     * Get grade levels by category.
     */
    public static List<GradeLevel> getByCategory(String category) {
        return Arrays.stream(values())
                .filter(gl -> gl.getCategory().equalsIgnoreCase(category))
                .sorted(java.util.Comparator.comparingInt(GradeLevel::getSortOrder))
                .toList();
    }

    /**
     * Check if a grade level code is valid.
     */
    public static boolean isValid(String code) {
        return Arrays.stream(values())
                .anyMatch(gl -> gl.name().equalsIgnoreCase(code));
    }

    /**
     * Default naming conventions per category.
     */
    public static String getDefaultNamingConvention(String category) {
        return switch (category.toUpperCase()) {
            case "NURSERY" -> "Nursery {level}";
            case "PRIMARY" -> "Primary {level}";
            case "JUNIOR_SECONDARY" -> "JSS {level}";
            case "SENIOR_SECONDARY" -> "SSS {level}";
            default -> "{name}";
        };
    }
}