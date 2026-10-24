package com.grapefruit.hellblazer.client;

import com.grapefruit.hellblazer.occult.CaseFile;
import com.grapefruit.hellblazer.occult.CaseType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/** How cases read: kinds, subjects, objectives, the handler's notes, clocks. */
public final class CaseText {
    private CaseText() {}

    public static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    public static String typeName(CaseType t) {
        return tr("case.geist_hellblazer.type." + t.key()).toUpperCase(Locale.ROOT);
    }

    private static ResourceLocation rl(String s) {
        ResourceLocation r = ResourceLocation.tryParse(s.startsWith("#") ? s.substring(1) : s);
        return r == null ? new ResourceLocation("minecraft", "air") : r;
    }

    public static Item item(CaseFile c) {
        Item it = ForgeRegistries.ITEMS.getValue(rl(c.target));
        return it == null ? Items.AIR : it;
    }

    /** What the case is about, by name: a creature, a cursed thing, a place. */
    public static String targetName(CaseFile c) {
        return switch (c.type) {
            case HUNT, TARGET -> EntityType.byString(c.target).map(t -> t.getDescription().getString()).orElse(c.target);
            case RELIC -> item(c).getDescription().getString();
            case HAUNTING -> tr("case.geist_hellblazer.target.haunting");
            case INVESTIGATE -> {
                ResourceLocation r = rl(c.target);
                yield c.site == 0 ? tr("biome." + r.getNamespace() + "." + r.getPath()) : tr("structure.geist_hellblazer." + r.getPath());
            }
        };
    }

    /** The objective in one line. */
    public static String objective(CaseFile c) {
        return tr("case.geist_hellblazer.objective." + c.type.key(), c.count, targetName(c), c.subject);
    }

    /** The handler's notes on the file (one of three per kind, picked by the case's seed). */
    public static String briefing(CaseFile c, String name) {
        int v = Math.floorMod(c.seed, 3);
        return tr("case.geist_hellblazer.brief." + c.type.key() + "." + v, c.count, targetName(c), name, c.subject);
    }

    /** The picture for a case that isn't a creature. */
    public static ItemStack picture(CaseFile c) {
        return switch (c.type) {
            case RELIC -> new ItemStack(item(c));
            case HAUNTING -> new ItemStack(Items.SOUL_LANTERN);
            case INVESTIGATE -> new ItemStack(c.site == 0 ? Items.COMPASS : Items.FILLED_MAP);
            default -> ItemStack.EMPTY;
        };
    }

    /** "1:23:45", "12:05" or "--" for no limit. */
    public static String clock(long ticks) {
        if (ticks <= 0) return "--";
        long s = ticks / 20L;
        long h = s / 3600L, m = (s / 60L) % 60L, sec = s % 60L;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    /** Ticks left on a case being worked (or its full length on the board); 0 = no limit. */
    public static long timeLeft(CaseFile c) {
        Minecraft mc = Minecraft.getInstance();
        if (!c.active) return c.ticks;
        if (c.expires <= 0 || mc.level == null) return 0;
        return Math.max(1L, c.expires - mc.level.getGameTime());
    }

    public static String denial(String why, int a, int b) {
        return switch (why) {
            case "items" -> tr("case.geist_hellblazer.deny.items", a, b);
            case "levels" -> tr("case.geist_hellblazer.deny.levels", a);
            default -> tr("case.geist_hellblazer.deny." + why);
        };
    }

    public static String compass(double dx, double dz) {
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        return dirs[Math.floorMod((int) Math.round(ang / 45.0D), 8)];
    }
}
