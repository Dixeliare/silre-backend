package com.longdx.silre_backend.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility class for generating URL-friendly slugs from text
 * 
 * Pattern:
 * - Convert to lowercase
 * - Remove accents/diacritics
 * - Replace spaces and special characters with hyphens
 * - Remove consecutive hyphens
 * - Trim leading/trailing hyphens
 * - Limit length
 */
public class SlugUtils {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern EDGES_DASHES = Pattern.compile("(^-|-$)");
    private static final Pattern MULTIPLE_DASHES = Pattern.compile("-+");
    private static final int MAX_SLUG_LENGTH = 350; // Match database column length

    /**
     * Generate a URL-friendly slug from a string
     * 
     * Examples:
     * - "Hello World!" -> "hello-world"
     * - "Café & Restaurant" -> "cafe-restaurant"
     * - "   Multiple   Spaces   " -> "multiple-spaces"
     * 
     * @param input Input string to convert to slug
     * @return URL-friendly slug, or null if input is null/empty
     */
    public static String slugify(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        // Normalize Unicode (remove accents/diacritics)
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        
        // Remove non-ASCII characters (accents, diacritics)
        normalized = NON_LATIN.matcher(normalized).replaceAll("");
        
        // Convert to lowercase
        normalized = normalized.toLowerCase(Locale.ENGLISH);
        
        // Replace whitespace with hyphens
        normalized = WHITESPACE.matcher(normalized).replaceAll("-");
        
        // Remove consecutive hyphens
        normalized = MULTIPLE_DASHES.matcher(normalized).replaceAll("-");
        
        // Remove leading/trailing hyphens
        normalized = EDGES_DASHES.matcher(normalized).replaceAll("");
        
        // Limit length
        if (normalized.length() > MAX_SLUG_LENGTH) {
            normalized = normalized.substring(0, MAX_SLUG_LENGTH);
            // Remove trailing hyphen if cut in the middle
            if (normalized.endsWith("-")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
        }
        
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Generate slug from title, with fallback to content if title is empty
     * 
     * @param title Post title (can be null)
     * @param content Post content (used as fallback)
     * @return Generated slug, or null if both title and content are empty
     */
    public static String generateSlugFromTitle(String title, String content) {
        // Prefer title if available
        if (title != null && !title.trim().isEmpty()) {
            String slug = slugify(title);
            if (slug != null && !slug.isEmpty()) {
                return slug;
            }
        }
        
        // Fallback to content (first 100 chars)
        if (content != null && !content.trim().isEmpty()) {
            String contentPreview = content.length() > 100 
                    ? content.substring(0, 100) 
                    : content;
            return slugify(contentPreview);
        }
        
        return null;
    }
}
