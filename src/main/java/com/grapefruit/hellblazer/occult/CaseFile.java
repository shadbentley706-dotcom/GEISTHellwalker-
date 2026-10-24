package com.grapefruit.hellblazer.occult;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** One case: on the board, or taken and being worked. Saved with the player; sent to their casebook. */
public final class CaseFile {
    public int id, count, progress, rep, xp, emeralds, seed, threat;
    /** How long it runs once taken (ticks, 0 = no limit). */
    public int ticks;
    public CaseType type = CaseType.HUNT;
    /** What it's about: an entity type, an item, a biome or a structure ("#ns:path" for a structure tag). */
    public String target = "";
    /** The file's name ("THE WEEPING HOUSE"); for a TARGET, the figure's name too. */
    public String name = "", subject = "";
    /** INVESTIGATE: 0 a biome, 1 a structure. */
    public int site;
    public boolean active, spawned;
    /** Game time it runs out (0 = never); game time the next thing comes out of a haunting. */
    public long expires, next;
    /** The marked place (TARGET, HAUNTING), and the dimension it was taken in. */
    public int x, y, z;
    public String dim = "";
    public UUID figure;

    public boolean done() {
        return progress >= count;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", id);
        t.putInt("Type", type.ordinal());
        t.putString("Target", target);
        t.putInt("Count", count);
        t.putInt("Progress", progress);
        t.putInt("Rep", rep);
        t.putInt("Xp", xp);
        t.putInt("Emeralds", emeralds);
        t.putInt("Seed", seed);
        t.putInt("Threat", threat);
        t.putInt("Ticks", ticks);
        t.putString("Name", name);
        t.putString("Subject", subject);
        t.putInt("Site", site);
        t.putBoolean("Active", active);
        t.putBoolean("Spawned", spawned);
        t.putLong("Expires", expires);
        t.putLong("Next", next);
        t.putInt("X", x);
        t.putInt("Y", y);
        t.putInt("Z", z);
        t.putString("Dim", dim);
        if (figure != null) t.putUUID("Figure", figure);
        return t;
    }

    public static CaseFile load(CompoundTag t) {
        CaseFile c = new CaseFile();
        c.id = t.getInt("Id");
        c.type = CaseType.byIndex(t.getInt("Type"));
        c.target = t.getString("Target");
        c.count = Math.max(1, t.getInt("Count"));
        c.progress = t.getInt("Progress");
        c.rep = t.getInt("Rep");
        c.xp = t.getInt("Xp");
        c.emeralds = t.getInt("Emeralds");
        c.seed = t.getInt("Seed");
        c.threat = t.getInt("Threat");
        c.ticks = t.getInt("Ticks");
        c.name = t.getString("Name");
        c.subject = t.getString("Subject");
        c.site = t.getInt("Site");
        c.active = t.getBoolean("Active");
        c.spawned = t.getBoolean("Spawned");
        c.expires = t.getLong("Expires");
        c.next = t.getLong("Next");
        c.x = t.getInt("X");
        c.y = t.getInt("Y");
        c.z = t.getInt("Z");
        c.dim = t.getString("Dim");
        if (t.hasUUID("Figure")) c.figure = t.getUUID("Figure");
        return c;
    }
}
