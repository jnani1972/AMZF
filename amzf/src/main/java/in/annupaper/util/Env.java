package in.annupaper.util;

/**
 * Environment variable utilities.
 */
public final class Env {
    
    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(key);
        }
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
    
    public static int getInt(String key, int defaultValue) {
        String value = get(key, null);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public static boolean getBool(String key, boolean defaultValue) {
        String value = get(key, null);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
    
    private Env() {}
}
