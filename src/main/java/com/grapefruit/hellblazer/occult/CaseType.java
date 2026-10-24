package com.grapefruit.hellblazer.occult;

import java.util.Locale;

/** The kinds of file the occult section works. */
public enum CaseType {
    /** Put down N of something that shouldn't be walking about. */
    HUNT,
    /** A named figure, waiting at a marked place with its followers. */
    TARGET,
    /** Go to a marked place after dark and put down what comes out of it. */
    HAUNTING,
    /** Recover N of something cursed and hand it over from the casebook. */
    RELIC,
    /** Set foot in a place (a biome or a structure) and look around. */
    INVESTIGATE;

    public static CaseType byIndex(int i) {
        CaseType[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
