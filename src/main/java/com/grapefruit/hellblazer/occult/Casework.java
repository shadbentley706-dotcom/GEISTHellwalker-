package com.grapefruit.hellblazer.occult;

import com.grapefruit.hellblazer.HellblazerMod;
import com.grapefruit.hellblazer.network.EventPacket;
import com.grapefruit.hellblazer.network.HbNetwork;
import com.grapefruit.hellblazer.network.SyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The specialist's file with the occult section: reputation (and the rank it buys), the cases on their board and the
 * ones they're working, and a few numbers for the record. Kept in the player's persisted data (it survives death),
 * changed only on the server, and sent to their casebook whenever it changes.
 */
@Mod.EventBusSubscriber(modid = HellblazerMod.MODID)
public final class Casework {
    private static final String ROOT = "GeistHellblazer";
    private static final Set<UUID> DIRTY = new HashSet<>();

    private Casework() {}

    static CompoundTag data(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = pd.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(ROOT, Tag.TAG_COMPOUND)) persisted.put(ROOT, new CompoundTag());
        return persisted.getCompound(ROOT);
    }

    // ================================================================ reputation

    public static int rep(Player p) {
        return data(p).getInt("Rep");
    }

    public static HbRank rank(Player p) {
        return HbRank.of(rep(p));
    }

    public static void addRep(ServerPlayer p, int n) {
        if (n <= 0) return;
        HbRank before = rank(p);
        CompoundTag d = data(p);
        d.putInt("Rep", d.getInt("Rep") + n);
        HbRank after = rank(p);
        if (after.ordinal() > before.ordinal()) event(p, EventPacket.RANK_UP, "", after.ordinal(), 0);
        dirty(p);
    }

    // ================================================================ cases

    public static List<CaseFile> cases(Player p) {
        List<CaseFile> out = new ArrayList<>();
        ListTag l = data(p).getList("Cases", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) out.add(CaseFile.load(l.getCompound(i)));
        return out;
    }

    public static void saveCases(ServerPlayer p, List<CaseFile> list) {
        ListTag l = new ListTag();
        for (CaseFile c : list) l.add(c.save());
        data(p).put("Cases", l);
        dirty(p);
    }

    public static int nextId(ServerPlayer p) {
        CompoundTag d = data(p);
        int n = d.getInt("NextId") + 1;
        d.putInt("NextId", n);
        return n;
    }

    public static long offersAt(Player p) {
        return data(p).getLong("OffersAt");
    }

    public static void setOffersAt(ServerPlayer p, long t) {
        data(p).putLong("OffersAt", t);
    }

    // ================================================================ the record

    public static void stat(ServerPlayer p, String key, int add) {
        CompoundTag d = data(p);
        CompoundTag s = d.getCompound("Stats");
        s.putInt(key, s.getInt(key) + add);
        d.put("Stats", s);
        dirty(p);
    }

    public static int stat(Player p, String key) {
        return data(p).getCompound("Stats").getInt(key);
    }

    static void event(ServerPlayer p, int kind, String text, int a, int b) {
        HbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EventPacket(kind, text, a, b));
    }

    // ================================================================ keeping the casebook up to date

    public static void dirty(ServerPlayer p) {
        DIRTY.add(p.getUUID());
    }

    public static CompoundTag snapshot(ServerPlayer p) {
        CompoundTag t = data(p).copy();
        t.putInt("Rank", rank(p).ordinal());
        return t;
    }

    public static void sync(ServerPlayer p) {
        HbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SyncPacket(snapshot(p)));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DIRTY.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            DIRTY.clear();
            return;
        }
        for (UUID id : DIRTY) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) sync(p);
        }
        DIRTY.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag d = data(p);
        if (!d.getBoolean("Welcomed")) {
            d.putBoolean("Welcomed", true);
            p.sendSystemMessage(Component.translatable("message.geist_hellblazer.welcome", Component.keybind("key.geist_hellblazer.casebook")).withStyle(ChatFormatting.DARK_RED));
        }
        Cases.ensureOffers(p);
        sync(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }
}
