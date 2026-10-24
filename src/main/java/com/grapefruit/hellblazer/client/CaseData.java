package com.grapefruit.hellblazer.client;

import com.grapefruit.hellblazer.HellblazerMod;
import com.grapefruit.hellblazer.occult.CaseFile;
import com.grapefruit.hellblazer.occult.Cases;
import com.grapefruit.hellblazer.occult.HbRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** The client's copy of the specialist's file (sent whenever it changes), and which case the HUD follows. */
@Mod.EventBusSubscriber(modid = HellblazerMod.MODID, value = Dist.CLIENT)
public final class CaseData {
    private static CompoundTag tag = new CompoundTag();
    private static final List<CaseFile> CASES = new ArrayList<>();
    private static int version, tracked = -1;
    private static boolean received;
    private static float shownRep = -1.0F;

    private CaseData() {}

    public static void apply(CompoundTag t) {
        tag = t == null ? new CompoundTag() : t;
        CASES.clear();
        ListTag l = tag.getList("Cases", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) CASES.add(CaseFile.load(l.getCompound(i)));
        if (tracked >= 0 && get(tracked) == null) tracked = -1;
        if (shownRep < 0.0F) shownRep = rep();
        version++;
        received = true;
    }

    public static boolean received() {
        return received;
    }

    public static int version() {
        return version;
    }

    public static int rep() {
        return tag.getInt("Rep");
    }

    public static HbRank rank() {
        return HbRank.byIndex(tag.getInt("Rank"));
    }

    public static float rankProgress() {
        HbRank r = rank(), n = r.next();
        if (n == null) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, (rep() - r.rep) / (float) (n.rep - r.rep)));
    }

    public static int stat(String key) {
        return tag.getCompound("Stats").getInt(key);
    }

    public static long offersAt() {
        return tag.getLong("OffersAt");
    }

    public static int maxActive() {
        return Cases.MAX_ACTIVE + (rank().ordinal() >= HbRank.HEXBREAKER.ordinal() ? 1 : 0);
    }

    public static List<CaseFile> cases() {
        return CASES;
    }

    public static List<CaseFile> offers() {
        List<CaseFile> out = new ArrayList<>();
        for (CaseFile c : CASES) if (!c.active) out.add(c);
        return out;
    }

    public static List<CaseFile> active() {
        List<CaseFile> out = new ArrayList<>();
        for (CaseFile c : CASES) if (c.active) out.add(c);
        return out;
    }

    public static CaseFile get(int id) {
        for (CaseFile c : CASES) if (c.id == id) return c;
        return null;
    }

    /** The case the HUD follows: the one picked in the casebook, else the first one being worked. */
    public static CaseFile tracked() {
        if (tracked >= 0) {
            CaseFile c = get(tracked);
            if (c != null && c.active) return c;
        }
        for (CaseFile c : CASES) if (c.active) return c;
        return null;
    }

    public static int trackedId() {
        CaseFile c = tracked();
        return c == null ? -1 : c.id;
    }

    public static void track(int id) {
        tracked = id;
    }

    /** The reputation counter, rolling toward the real figure (call once a frame). */
    public static int rollRep(float frameTicks) {
        float target = rep();
        if (shownRep < 0.0F) shownRep = target;
        float d = target - shownRep;
        shownRep += Math.abs(d) < 1.0F ? d : d * Math.min(1.0F, 0.12F * frameTicks) + Math.signum(d) * 0.5F;
        return Math.round(shownRep);
    }

    /** The counter as last rolled. */
    public static int shownRep() {
        return shownRep < 0.0F ? rep() : Math.round(shownRep);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        tag = new CompoundTag();
        CASES.clear();
        received = false;
        tracked = -1;
        shownRep = -1.0F;
    }
}
