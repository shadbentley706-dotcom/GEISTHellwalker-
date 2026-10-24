package com.grapefruit.hellblazer.occult;

import com.grapefruit.hellblazer.network.EventPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The casebook's board: the files on offer (three, four from EXORCIST; a fresh board every ten minutes, or sooner for a
 * few levels of experience), the ones being worked (two, three from HEXBREAKER), and everything that moves a file
 * along - kills, places reached, relics handed over, the dead put back down - to closing it and paying out (standing
 * with the section, experience and emeralds), or writing it off when the clock runs out.
 *
 * Five kinds of file, all of them about things already in the world, seen the section's way:
 * <ul>
 * <li><b>HUNT</b> - put down N of something that shouldn't be walking about (the drowned, witches, phantoms, the
 * illager cult, endermen, wither skeletons, blazes...).</li>
 * <li><b>TARGET</b> - a named figure (a witch, a cult evoker, a wither skeleton champion...) waiting at a marked place
 * 140-240 blocks out with followers about it, three times as hard to kill.</li>
 * <li><b>HAUNTING</b> - a marked place 80-160 blocks out where the dead won't stay down. After dark, stand there and
 * they come up out of the ground round you, again and again, until enough of them are put back.</li>
 * <li><b>RELIC</b> - recover N of something cursed (wither skeleton skulls, ghast tears, crying obsidian, an echo
 * shard...) and send it in from the casebook.</li>
 * <li><b>INVESTIGATE</b> - set foot in a place no one should go (a swamp hut, a ruined portal, a stronghold, the deep
 * dark, a soul sand valley...).</li>
 * </ul>
 */
public final class Cases {
    public static final int OFFERS = 3, MAX_ACTIVE = 2, REROLL_LEVELS = 3;
    public static final long REFRESH = 12000L;
    public static final String HAUNT_TAG = "HbHaunting", TARGET_OWNER = "HbTargetOwner", TARGET_CASE = "HbTargetCase";
    private static final UUID TARGET_HEALTH = UUID.fromString("4d2b8f61-0c7e-4a35-9e12-7b6a51c3e8f4");

    private static final String[] ADJ = {"WEEPING", "HOLLOW", "DROWNED", "BURNT", "SILENT", "CROOKED", "BLACK", "PALE", "HANGED", "GRINNING",
            "BROKEN", "RUSTED", "ROTTEN", "BLIND", "LAST", "CRIMSON", "SALTED", "UNQUIET", "NAMELESS", "SMILING", "WIDOWED", "THIRTEENTH"};
    private static final String[] NOUN = {"HOUSE", "ROAD", "CHAPEL", "ORCHARD", "BELL", "MIRROR", "CRADLE", "WELL", "LANTERN", "DOLL", "SAINT",
            "CHOIR", "KEY", "CROSSING", "ROOM", "PIER", "STAIR", "WINDOW", "GARDEN", "HYMN", "LEDGER", "MARE"};
    private static final String[] FIRST = {"Agnes", "Silas", "Mother", "Brother", "Old", "Sister", "Ezekiel", "Morwenna", "Thaddeus", "Hester",
            "Josiah", "Ophelia", "Barnaby", "Constance", "Ambrose", "Lilith"};
    private static final String[] LAST = {"Hollis", "Crane", "Marlowe", "Vane", "Ashby", "Thorne", "Blackwood", "Grimsby", "Pell", "Rook",
            "Mercy", "Coldharbour", "Wych", "Fennick", "Gallow", "Sallow"};
    private static final String[] EPITHET = {"the Widow", "the Deacon", "Saltmouth", "the Choirmaster", "Nine Fingers", "the Ashen",
            "Long Shadow", "the Lamplighter", "the Drowned", "Bellringer", "the Smiling Man", "Candlewick"};

    /** Something to hunt: how dangerous one is (sets the pay), how many to ask for, the rank it starts at, and where. */
    private record Mark(String id, int danger, int min, int max, int rank, String dim) {}

    private static final Mark[] MARKS = {
            new Mark("minecraft:zombie", 5, 10, 18, 0, "overworld"), new Mark("minecraft:drowned", 7, 6, 12, 0, "overworld"),
            new Mark("minecraft:witch", 16, 2, 4, 0, "overworld"), new Mark("minecraft:phantom", 12, 3, 6, 0, "overworld"),
            new Mark("minecraft:zombie_villager", 9, 3, 6, 1, "overworld"), new Mark("minecraft:stray", 9, 5, 10, 1, "overworld"),
            new Mark("minecraft:husk", 7, 6, 12, 1, "overworld"), new Mark("minecraft:pillager", 11, 4, 8, 1, "overworld"),
            new Mark("minecraft:vindicator", 17, 2, 5, 2, "overworld"), new Mark("minecraft:enderman", 20, 2, 5, 2, "overworld"),
            new Mark("minecraft:cave_spider", 9, 6, 12, 2, "overworld"), new Mark("minecraft:evoker", 55, 1, 2, 4, "overworld"),
            new Mark("minecraft:vex", 14, 3, 6, 4, "overworld"),
            new Mark("minecraft:magma_cube", 7, 6, 12, 0, "the_nether"), new Mark("minecraft:wither_skeleton", 24, 3, 6, 2, "the_nether"),
            new Mark("minecraft:blaze", 20, 4, 8, 2, "the_nether"), new Mark("minecraft:ghast", 28, 2, 4, 3, "the_nether"),
            new Mark("minecraft:enderman", 20, 6, 12, 0, "the_end"), new Mark("minecraft:shulker", 28, 3, 6, 4, "the_end")};

    /** A cursed thing to recover: what one is worth, how many to ask for, the rank it starts at. */
    private record Relic(String id, int value, int min, int max, int rank) {}

    private static final Relic[] RELICS = {
            new Relic("minecraft:rotten_flesh", 1, 24, 48, 0), new Relic("minecraft:bone", 2, 16, 32, 0), new Relic("minecraft:spider_eye", 3, 8, 16, 0),
            new Relic("minecraft:ender_pearl", 12, 3, 8, 0), new Relic("minecraft:phantom_membrane", 10, 3, 6, 0),
            new Relic("minecraft:soul_sand", 2, 16, 32, 1), new Relic("minecraft:nether_wart", 3, 12, 24, 1), new Relic("minecraft:candle", 4, 6, 12, 1),
            new Relic("minecraft:crying_obsidian", 14, 4, 8, 2), new Relic("minecraft:ghast_tear", 38, 1, 3, 2),
            new Relic("minecraft:wither_skeleton_skull", 70, 1, 2, 3), new Relic("minecraft:echo_shard", 50, 1, 3, 4),
            new Relic("minecraft:totem_of_undying", 120, 1, 1, 4), new Relic("minecraft:dragon_breath", 40, 1, 3, 5)};

    /** A place: a biome or a structure ("#" = a structure tag), the rank it starts at, and its dimension. */
    private record Site(String id, int rank, String dim) {}

    private static final Site[] BIOMES = {
            new Site("minecraft:swamp", 0, "overworld"), new Site("minecraft:dark_forest", 0, "overworld"), new Site("minecraft:old_growth_spruce_taiga", 0, "overworld"),
            new Site("minecraft:mangrove_swamp", 1, "overworld"), new Site("minecraft:snowy_taiga", 1, "overworld"), new Site("minecraft:badlands", 1, "overworld"),
            new Site("minecraft:dripstone_caves", 2, "overworld"), new Site("minecraft:deep_dark", 4, "overworld"),
            new Site("minecraft:soul_sand_valley", 0, "the_nether"), new Site("minecraft:crimson_forest", 0, "the_nether"),
            new Site("minecraft:warped_forest", 1, "the_nether"), new Site("minecraft:basalt_deltas", 2, "the_nether")};
    private static final Site[] STRUCTURES = {
            new Site("minecraft:swamp_hut", 0, "overworld"), new Site("#minecraft:ruined_portal", 0, "overworld"), new Site("#minecraft:mineshaft", 0, "overworld"),
            new Site("minecraft:desert_pyramid", 1, "overworld"), new Site("minecraft:jungle_pyramid", 1, "overworld"),
            new Site("minecraft:pillager_outpost", 1, "overworld"), new Site("minecraft:trail_ruins", 1, "overworld"),
            new Site("minecraft:stronghold", 2, "overworld"), new Site("minecraft:mansion", 4, "overworld"), new Site("minecraft:ancient_city", 4, "overworld"),
            new Site("minecraft:fortress", 2, "the_nether"), new Site("minecraft:bastion_remnant", 3, "the_nether")};

    /** A named figure: what it is, what follows it about, the rank it starts at, its dimension. */
    private record Figure(String id, String follower, int rank, String dim) {}

    private static final Figure[] FIGURES = {
            new Figure("minecraft:witch", "minecraft:zombie", 1, "overworld"), new Figure("minecraft:zombie_villager", "minecraft:zombie", 1, "overworld"),
            new Figure("minecraft:stray", "minecraft:skeleton", 1, "overworld"), new Figure("minecraft:vindicator", "minecraft:pillager", 2, "overworld"),
            new Figure("minecraft:witch", "minecraft:witch", 3, "overworld"), new Figure("minecraft:evoker", "minecraft:vindicator", 4, "overworld"),
            new Figure("minecraft:wither_skeleton", "minecraft:wither_skeleton", 3, "the_nether"),
            new Figure("minecraft:piglin_brute", "minecraft:piglin", 3, "the_nether")};

    /** What comes up out of the ground at a haunting, by the rank it starts at. */
    private static final String[][] RISEN = {{"minecraft:zombie", "minecraft:skeleton"}, {"minecraft:husk", "minecraft:stray"},
            {"minecraft:zombie_villager", "minecraft:drowned"}, {"minecraft:wither_skeleton"}, {"minecraft:vex"}};

    private Cases() {}

    private static long now(ServerPlayer p) {
        return p.server.overworld().getGameTime();
    }

    private static String dim(ServerPlayer p) {
        return p.level().dimension().location().getPath();
    }

    private static void event(ServerPlayer p, int kind, String text, int a, int b) {
        Casework.event(p, kind, text, a, b);
    }

    public static int offers(ServerPlayer p) {
        return OFFERS + (Casework.rank(p).ordinal() >= HbRank.EXORCIST.ordinal() ? 1 : 0);
    }

    public static int maxActive(ServerPlayer p) {
        return MAX_ACTIVE + (Casework.rank(p).ordinal() >= HbRank.HEXBREAKER.ordinal() ? 1 : 0);
    }

    // ================================================================ the board

    /** Top the board up (clearing out stale files every ten minutes). */
    public static void ensureOffers(ServerPlayer p) {
        List<CaseFile> all = Casework.cases(p);
        long now = now(p);
        boolean changed = false;
        if (now - Casework.offersAt(p) >= REFRESH || now < Casework.offersAt(p)) {
            changed = all.removeIf(c -> !c.active);
            Casework.setOffersAt(p, now);
        }
        int offered = 0;
        for (CaseFile c : all) if (!c.active) offered++;
        RandomSource r = p.getRandom();
        int want = offers(p);
        while (offered < want) {
            all.add(generate(p, r));
            offered++;
            changed = true;
        }
        if (changed) Casework.saveCases(p, all);
    }

    /** New leads, for a few levels of experience. */
    public static void reroll(ServerPlayer p) {
        if (p.experienceLevel < REROLL_LEVELS && !p.getAbilities().instabuild) {
            event(p, EventPacket.DENIED, "levels", REROLL_LEVELS, 0);
            return;
        }
        if (!p.getAbilities().instabuild) p.giveExperienceLevels(-REROLL_LEVELS);
        List<CaseFile> all = Casework.cases(p);
        all.removeIf(c -> !c.active);
        Casework.saveCases(p, all);
        Casework.setOffersAt(p, now(p));
        ensureOffers(p);
    }

    private static int pick(RandomSource r, int[] weights) {
        int total = 0;
        for (int w : weights) total += w;
        int k = r.nextInt(Math.max(1, total));
        for (int i = 0; i < weights.length; i++) {
            k -= weights[i];
            if (k < 0) return i;
        }
        return 0;
    }

    private static <T> T choose(RandomSource r, List<T> list) {
        return list.get(r.nextInt(list.size()));
    }

    static CaseFile generate(ServerPlayer p, RandomSource r) {
        int rank = Casework.rank(p).ordinal();
        String dim = dim(p);
        float bonus = 1.0F + 0.15F * rank;
        CaseFile c = new CaseFile();
        c.id = Casework.nextId(p);
        c.seed = r.nextInt(1 << 20);
        c.name = "THE " + ADJ[r.nextInt(ADJ.length)] + " " + NOUN[r.nextInt(NOUN.length)];
        // HUNT, TARGET, HAUNTING, RELIC, INVESTIGATE
        boolean end = dim.equals("the_end");
        int[] w = {36, rank >= 1 && !end ? 14 : 0, end ? 0 : 20, 18, 16};
        CaseType type = CaseType.byIndex(pick(r, w));
        switch (type) {
            case HUNT -> {
                List<Mark> pool = new ArrayList<>();
                for (Mark m : MARKS) if (m.rank() <= rank && m.dim().equals(dim)) pool.add(m);
                if (pool.isEmpty()) for (Mark m : MARKS) if (m.rank() <= rank && m.dim().equals("overworld")) pool.add(m);
                Mark m = choose(r, pool);
                c.target = m.id();
                c.count = Mth.nextInt(r, m.min(), m.max());
                c.rep = Math.round(m.danger() * c.count * 0.8F * bonus);
                c.ticks = 24000;
            }
            case TARGET -> {
                List<Figure> pool = new ArrayList<>();
                for (Figure f : FIGURES) if (f.rank() <= rank && f.dim().equals(dim)) pool.add(f);
                if (pool.isEmpty()) for (Figure f : FIGURES) if (f.rank() <= rank && f.dim().equals("overworld")) pool.add(f);
                Figure f = choose(r, pool);
                c.target = f.id();
                c.count = 1;
                c.subject = FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)] + ", " + EPITHET[r.nextInt(EPITHET.length)];
                c.rep = 220 + 50 * rank + r.nextInt(60);
                c.ticks = 36000;
            }
            case HAUNTING -> {
                c.target = "geist_hellblazer:haunting";
                c.count = 6 + 2 * rank + r.nextInt(4);
                c.rep = Math.round(12 * c.count * bonus) + 40;
                c.ticks = 48000;
            }
            case RELIC -> {
                List<Relic> pool = new ArrayList<>();
                for (Relic rl : RELICS) if (rl.rank() <= rank) pool.add(rl);
                Relic rl = choose(r, pool);
                c.target = rl.id();
                c.count = Mth.nextInt(r, rl.min(), rl.max());
                c.rep = Math.max(30, Math.round(rl.value() * c.count * 1.1F * bonus));
            }
            case INVESTIGATE -> {
                boolean structure = r.nextBoolean();
                Site[] sites = structure ? STRUCTURES : BIOMES;
                List<Site> pool = new ArrayList<>();
                for (Site s : sites) if (s.rank() <= rank && s.dim().equals(dim)) pool.add(s);
                if (pool.isEmpty()) for (Site s : sites) if (s.rank() <= rank && s.dim().equals("overworld")) pool.add(s);
                Site s = choose(r, pool);
                c.target = s.id();
                c.site = structure ? 1 : 0;
                c.count = 1;
                c.rep = (structure ? 110 : 90) + 30 * s.rank() + 10 * rank;
                c.ticks = 48000;
            }
        }
        c.type = type;
        c.xp = Math.max(10, c.rep / 2);
        c.emeralds = Mth.clamp(c.rep / 60, 1, 24);
        c.threat = Mth.clamp(1 + c.rep / 110, 1, 5);
        return c;
    }

    // ================================================================ what the specialist does with it

    public static void accept(ServerPlayer p, int id) {
        List<CaseFile> all = Casework.cases(p);
        int active = 0;
        for (CaseFile c : all) if (c.active) active++;
        for (CaseFile c : all) {
            if (c.id != id || c.active) continue;
            if (active >= maxActive(p)) {
                event(p, EventPacket.DENIED, "full", 0, 0);
                return;
            }
            if ((c.type == CaseType.TARGET || c.type == CaseType.HAUNTING) && p.level().dimension() == net.minecraft.world.level.Level.END) {
                event(p, EventPacket.DENIED, "end", 0, 0);
                return;
            }
            c.active = true;
            c.dim = p.level().dimension().location().toString();
            c.expires = c.ticks > 0 ? now(p) + c.ticks : 0L;
            if (c.type == CaseType.TARGET || c.type == CaseType.HAUNTING) {
                double a = p.getRandom().nextDouble() * Math.PI * 2.0D;
                double d = c.type == CaseType.TARGET ? 140.0D + p.getRandom().nextDouble() * 100.0D : 80.0D + p.getRandom().nextDouble() * 80.0D;
                c.x = Mth.floor(p.getX() + Math.cos(a) * d);
                c.z = Mth.floor(p.getZ() + Math.sin(a) * d);
                c.y = Mth.floor(p.getY());
            }
            Casework.saveCases(p, all);
            Casework.stat(p, "accepted", 1);
            event(p, EventPacket.ACCEPTED, c.name, c.id, 0);
            ensureOffers(p);
            return;
        }
    }

    public static void abandon(ServerPlayer p, int id) {
        List<CaseFile> all = Casework.cases(p);
        if (all.removeIf(c -> c.id == id && c.active)) {
            Casework.saveCases(p, all);
            Casework.stat(p, "abandoned", 1);
            ensureOffers(p);
        }
    }

    /** Send in the relics for a RELIC file. */
    public static void deliver(ServerPlayer p, int id) {
        List<CaseFile> all = Casework.cases(p);
        for (CaseFile c : all) {
            if (c.id != id || !c.active || c.type != CaseType.RELIC) continue;
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(c.target));
            if (item == null || item == Items.AIR) return;
            int have = 0;
            for (ItemStack s : p.getInventory().items) if (s.is(item)) have += s.getCount();
            if (have < c.count) {
                event(p, EventPacket.DENIED, "items", have, c.count);
                return;
            }
            int left = c.count;
            for (ItemStack s : p.getInventory().items) {
                if (left <= 0) break;
                if (!s.is(item)) continue;
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
            c.progress = c.count;
            complete(p, all, c);
            return;
        }
    }

    // ================================================================ progress

    /** The specialist put something down. */
    public static void onKill(ServerPlayer p, LivingEntity victim) {
        if (victim instanceof Enemy) Casework.stat(p, "kills", 1);
        ResourceLocation key = EntityType.getKey(victim.getType());
        String id = key == null ? "" : key.toString();
        int haunting = victim.getPersistentData().getInt(HAUNT_TAG);
        List<CaseFile> all = Casework.cases(p);
        CaseFile finished = null;
        boolean changed = false;
        for (CaseFile c : all) {
            if (!c.active || c.done()) continue;
            boolean counts = (c.type == CaseType.HUNT && c.target.equals(id)) || (c.type == CaseType.HAUNTING && haunting == c.id);
            if (counts) {
                c.progress++;
                changed = true;
                if (c.done() && finished == null) finished = c;
            }
        }
        if (finished != null) complete(p, all, finished);
        else if (changed) Casework.saveCases(p, all);
    }

    /** A named figure went down. */
    public static void onTargetDown(ServerPlayer p, int caseId) {
        List<CaseFile> all = Casework.cases(p);
        for (CaseFile c : all) {
            if (c.id == caseId && c.active) {
                c.progress = c.count;
                Casework.stat(p, "targets", 1);
                complete(p, all, c);
                return;
            }
        }
    }

    private static void complete(ServerPlayer p, List<CaseFile> all, CaseFile c) {
        all.remove(c);
        Casework.saveCases(p, all);
        Casework.addRep(p, c.rep);
        p.giveExperiencePoints(c.xp);
        ItemStack pay = new ItemStack(Items.EMERALD, c.emeralds);
        if (!p.getInventory().add(pay)) p.drop(pay, false);
        Casework.stat(p, "closed", 1);
        event(p, EventPacket.CLOSED, c.name, c.rep, c.emeralds);
        ensureOffers(p);
    }

    // ================================================================ the clock, places, figures and hauntings (every second)

    public static void tick(ServerPlayer p) {
        long now = now(p);
        List<CaseFile> all = Casework.cases(p);
        boolean changed = false;
        CaseFile finished = null;
        Iterator<CaseFile> it = all.iterator();
        while (it.hasNext()) {
            CaseFile c = it.next();
            if (!c.active) continue;
            if (c.expires > 0 && now > c.expires) {
                it.remove();
                changed = true;
                Casework.stat(p, "failed", 1);
                event(p, EventPacket.FAILED, c.name, c.id, 0);
                continue;
            }
            if (finished != null) continue;
            if (c.type == CaseType.INVESTIGATE && (c.site == 0 ? inBiome(p, c.target) : p.tickCount % 40 < 20 && inStructure(p, c.target))) {
                c.progress = c.count;
                finished = c;
            } else if (c.type == CaseType.TARGET) {
                changed |= tickTarget(p, c);
            } else if (c.type == CaseType.HAUNTING) {
                changed |= tickHaunting(p, c, now);
            }
        }
        if (finished != null) complete(p, all, finished);
        else if (changed) Casework.saveCases(p, all);
        if (now - Casework.offersAt(p) >= REFRESH) ensureOffers(p);
    }

    private static boolean inBiome(ServerPlayer p, String biome) {
        return p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString().equals(biome)).orElse(false);
    }

    private static boolean inStructure(ServerPlayer p, String id) {
        ServerLevel level = p.serverLevel();
        BlockPos pos = p.blockPosition();
        try {
            if (id.startsWith("#")) {
                TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, new ResourceLocation(id.substring(1)));
                return level.structureManager().getStructureWithPieceAt(pos, tag).isValid();
            }
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(id));
            return level.structureManager().getStructureWithPieceAt(pos, key).isValid();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The figure waits at its mark; it's put in the world when the specialist comes within 80 blocks. */
    private static boolean tickTarget(ServerPlayer p, CaseFile c) {
        ServerLevel level = p.serverLevel();
        if (!level.dimension().location().toString().equals(c.dim)) return false;
        double dx = p.getX() - (c.x + 0.5D), dz = p.getZ() - (c.z + 0.5D);
        double d2 = dx * dx + dz * dz;
        if (c.spawned) {
            if (d2 < 40.0D * 40.0D && (c.figure == null || level.getEntity(c.figure) == null)) {
                c.spawned = false;                                                  // it slipped away (unloaded): it'll be back
                return true;
            }
            return false;
        }
        if (d2 > 80.0D * 80.0D || !level.hasChunkAt(new BlockPos(c.x, c.y, c.z))) return false;
        EntityType<?> type = EntityType.byString(c.target).orElse(null);
        if (type == null) return false;
        RandomSource r = level.getRandom();
        BlockPos at = dryGround(level, c.x, c.z, p.getBlockY(), r);
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) return false;
        mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, r.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        mob.setCustomName(Component.literal(c.subject).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        mob.setCustomNameVisible(true);
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getModifier(TARGET_HEALTH) == null) {
            hp.addPermanentModifier(new AttributeModifier(TARGET_HEALTH, "Hellblazer target", 2.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        mob.setHealth(mob.getMaxHealth());
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 72000, 0, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 72000, 0, false, false));
        for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0.0F);
        mob.setPersistenceRequired();
        mob.getPersistentData().putUUID(TARGET_OWNER, p.getUUID());
        mob.getPersistentData().putInt(TARGET_CASE, c.id);
        level.addFreshEntity(mob);
        String followerId = c.target;
        for (Figure f : FIGURES) if (f.id().equals(c.target)) followerId = f.follower();
        EntityType<?> followerType = EntityType.byString(followerId).orElse(null);
        int followers = 2 + r.nextInt(2) + (Casework.rank(p).ordinal() >= HbRank.HEXBREAKER.ordinal() ? 1 : 0);
        for (int i = 0; followerType != null && i < followers; i++) {
            BlockPos g = dryGround(level, at.getX() + r.nextInt(7) - 3, at.getZ() + r.nextInt(7) - 3, at.getY(), r);
            Entity fe = followerType.create(level);
            if (!(fe instanceof Mob follower)) continue;
            follower.moveTo(g.getX() + 0.5D, g.getY(), g.getZ() + 0.5D, r.nextFloat() * 360.0F, 0.0F);
            follower.finalizeSpawn(level, level.getCurrentDifficultyAt(g), MobSpawnType.EVENT, null, null);
            follower.setPersistenceRequired();
            level.addFreshEntity(follower);
        }
        level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.6F);
        c.spawned = true;
        c.figure = mob.getUUID();
        c.y = at.getY();
        return true;
    }

    /** After dark, stand at the marked place and the dead come up round you, a few at a time, until enough are put back. */
    private static boolean tickHaunting(ServerPlayer p, CaseFile c, long now) {
        ServerLevel level = p.serverLevel();
        if (!level.dimension().location().toString().equals(c.dim)) return false;
        double dx = p.getX() - (c.x + 0.5D), dz = p.getZ() - (c.z + 0.5D);
        double d2 = dx * dx + dz * dz;
        if (d2 > 64.0D * 64.0D || !level.hasChunkAt(new BlockPos(c.x, 64, c.z))) return false;
        int gy = surface(level, c.x, c.z, p.getBlockY());
        boolean changed = false;
        if (c.y != gy) {
            c.y = gy;
            changed = true;
        }
        // the place marks itself: souls rising, a chill
        level.sendParticles(ParticleTypes.SOUL, c.x + 0.5D, gy + 0.5D, c.z + 0.5D, 3, 1.2D, 0.3D, 1.2D, 0.02D);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2.0D / 16.0D;
            double x = c.x + 0.5D + Math.cos(a) * 12.0D, z = c.z + 0.5D + Math.sin(a) * 12.0D;
            int y = surface(level, (int) Math.floor(x), (int) Math.floor(z), gy);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.1D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        boolean dark = level.isNight() || level.getMaxLocalRawBrightness(p.blockPosition()) < 7 || !level.dimensionType().hasSkyLight();
        if (!dark || d2 > 12.0D * 12.0D || now < c.next) return changed;
        // up they come
        RandomSource r = level.getRandom();
        int rank = Casework.rank(p).ordinal();
        List<String> pool = new ArrayList<>();
        for (int k = 0; k <= Math.min(rank, RISEN.length - 1); k++) for (String s : RISEN[k]) pool.add(s);
        int n = 2 + rank / 2;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2.0D, d = 5.0D + r.nextDouble() * 6.0D;
            BlockPos at = dryGround(level, (int) Math.floor(c.x + Math.cos(a) * d), (int) Math.floor(c.z + Math.sin(a) * d), gy, r);
            EntityType<?> type = EntityType.byString(pool.get(r.nextInt(pool.size()))).orElse(null);
            if (type == null) continue;
            Entity e = type.create(level);
            if (!(e instanceof Mob mob)) continue;
            mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, r.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
            if (mob.isInvertedHealAndHarm() && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
                mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
            }
            mob.getPersistentData().putInt(HAUNT_TAG, c.id);
            level.addFreshEntity(mob);
            mob.setTarget(p);
            level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, 12, 0.3D, 0.6D, 0.3D, 0.03D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5D, at.getY() + 0.2D, at.getZ() + 0.5D, 8, 0.3D, 0.2D, 0.3D, 0.01D);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.5F, 0.6F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 0.8F, 0.5F);
        c.next = now + 240L;
        return true;
    }

    /**
     * The ground at (x, z): the surface under open sky; under a roof (the nether), the floor with room to stand that's
     * nearest the height given, so nothing is put on top of the bedrock or inside the rock.
     */
    static int surface(ServerLevel level, int x, int z, int nearY) {
        if (!level.dimensionType().hasCeiling()) return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int lo = level.getMinBuildHeight() + 1, hi = level.getMinBuildHeight() + level.dimensionType().logicalHeight() - 3;
        int best = nearY, bestD = Integer.MAX_VALUE;
        for (int y = lo; y <= hi; y++) {
            m.set(x, y, z);
            if (!level.getBlockState(m).isAir() || !level.getBlockState(m.above()).isAir()) continue;
            BlockPos below = m.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
            int d = Math.abs(y - nearY);
            if (d < bestD) {
                bestD = d;
                best = y;
            }
        }
        return best;
    }

    /** Somewhere to stand near (x, z): the ground, avoiding water and lava where it can. */
    static BlockPos dryGround(ServerLevel level, int x, int z, int nearY, RandomSource r) {
        BlockPos best = null;
        for (int i = 0; i < 12; i++) {
            int px = x + (i == 0 ? 0 : r.nextInt(25) - 12), pz = z + (i == 0 ? 0 : r.nextInt(25) - 12);
            BlockPos pos = new BlockPos(px, surface(level, px, pz, nearY), pz);
            if (best == null) best = pos;
            if (level.getFluidState(pos.below()).isEmpty() && level.getFluidState(pos).isEmpty()) return pos;
        }
        return best;
    }
}
