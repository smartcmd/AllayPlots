package me.daoge.allayplots.plot;

import java.util.Locale;

public final class PlotFlagValue {
    private PlotFlagValue() {
    }

    public static Boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true", "t", "yes", "y", "on", "allow", "1" -> true;
            case "false", "f", "no", "n", "off", "deny", "0" -> false;
            default -> null;
        };
    }

    public static boolean isReset(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "default", "reset", "unset", "remove", "clear" -> true;
            default -> false;
        };
    }

    public static String format(boolean value) {
        return value ? "true" : "false";
    }
}
