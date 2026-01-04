package me.daoge.allayplots.plot;

import java.util.Locale;

public enum PlotFlag {
    ENTRY(true),
    BUILD(false),
    PVP(true),
    PVE(true),
    DAMAGE(true);

    private final boolean defaultValue;

    PlotFlag(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getLowerCaseName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean defaultValue() {
        return defaultValue;
    }
}
