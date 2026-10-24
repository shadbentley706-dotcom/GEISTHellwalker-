package com.grapefruit.hellblazer.occult;

/**
 * Standing in the occult section, earned by closing cases (reputation). Each rank opens harder files - and pays more
 * for them.
 */
public enum HbRank {
    NOBODY("NOBODY", 0),
    CONSULTANT("CONSULTANT", 150),
    INVESTIGATOR("INVESTIGATOR", 500),
    EXORCIST("EXORCIST", 1200),
    HEXBREAKER("HEXBREAKER", 2400),
    DEMONOLOGIST("DEMONOLOGIST", 4000),
    HELLBLAZER("HELLBLAZER", 6500);

    public final String title;
    public final int rep;

    HbRank(String title, int rep) {
        this.title = title;
        this.rep = rep;
    }

    public static HbRank of(int rep) {
        HbRank best = NOBODY;
        for (HbRank r : values()) if (rep >= r.rep) best = r;
        return best;
    }

    public static HbRank byIndex(int i) {
        HbRank[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    /** The next rank up, or null at the top. */
    public HbRank next() {
        HbRank[] v = values();
        return ordinal() + 1 < v.length ? v[ordinal() + 1] : null;
    }
}
