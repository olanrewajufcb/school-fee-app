package com.fee.app.schoolfeeapp.common.utils;

import java.util.regex.Pattern;

public final class PhoneNumberNormalizer {

    private static final Pattern NON_DIGITS = Pattern.compile("\\D");

    private PhoneNumberNormalizer() {
        // Utility class
    }

    /**
     * Normalize Nigerian phone numbers to canonical E.164-like format:
     * 2348031234567
     *
     * Accepted inputs:
     * - 08031234567
     * - 8031234567
     * - +2348031234567
     * - 2348031234567
     * - 002348031234567
     * - +234 803 123 4567
     */
    public static String normalize(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }

        // Remove spaces, dashes, brackets, etc.
        String digits = NON_DIGITS.matcher(rawPhone.trim()).replaceAll("");

        if (digits.isEmpty()) {
            return null;
        }

        // Remove international prefix 00
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }

        /*
         * Convert to local Nigerian format first (0XXXXXXXXXX)
         */
        if (digits.startsWith("234")) {
            // 2348031234567 -> 08031234567
            if (digits.length() != 13) {
                throw new IllegalArgumentException(
                        "Invalid Nigerian number length: " + rawPhone);
            }

            digits = "0" + digits.substring(3);

        } else if (!digits.startsWith("0")) {
            // 8031234567 -> 08031234567
            if (digits.length() == 10) {
                digits = "0" + digits;
            }
        }

        // Final local format validation
        if (!digits.matches("^0(70|71|80|81|90|91)\\d{8}$")) {
            throw new IllegalArgumentException(
                    "Invalid Nigerian phone number: " + rawPhone);
        }

        // Canonical storage format: 2348031234567
        return "234" + digits.substring(1);
    }

    /**
     * Validate Nigerian phone number.
     */
    public static boolean isValid(String rawPhone) {
        try {
            return normalize(rawPhone) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Format for UI display.
     * Example:
     * 2348031234567 -> 0803 123 4567
     */
    public static String formatForDisplay(String rawPhone) {
        String normalized = normalize(rawPhone);

        if (normalized == null) {
            return null;
        }

        String local = "0" + normalized.substring(3);

        return local.substring(0, 4) + " " +
                local.substring(4, 7) + " " +
                local.substring(7);
    }
}