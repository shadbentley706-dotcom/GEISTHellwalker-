package com.geist.event;

import com.geist.GeistMod;
import com.geist.item.Mt7cBreach;
import com.geist.item.Mt7cItem;
import com.geist.network.GeistNetwork;
import com.geist.network.Mt7cAnimPacket;
import com.geist.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The MT-7C on the server: the mode change, the breach thrust (the blocks go one layer per tick, so the break runs
 * forward through the rock like a crack), the recovery vent, and quiet digging (no vibrations from its blocks).
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID)
public final class Mt7cEvents {
    /** Ticks between letting go and the first layer breaking: the head has to get there first. */
    private static final int IMPACT_DELAY = 2;

    /** A breach in progress. */
    private static final class Breach {
        final List<List<BlockPos>> layers;
        final ResourceKey<Level> dim;
        final int mode;
        final Vec3 from;
        final long ventUntil;
        int next, broken;
        long nextTick;

        Breach(List<List<BlockPos>> layers, ResourceKey<Level> dim, int mode, Vec3 from, long nextTick, long ventUntil) {
            this.layers = layers;
            this.dim = dim;
            this.mode = mode;
            this.from = from;
            this.nextTick = nextTick;
            this.ventUntil = ventUntil;
        }
    }

    private static final Map<UUID, Breach> BREACHES = new HashMap<>();

    private Mt7cEvents() {}

    private static void send(ServerPlayer p, int event, int a, int b) {
        GeistNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new Mt7cAnimPacket(p.getId(), event, a, b));
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static boolean holding(Player p) {
        return p.getMainHandItem().getItem() instanceof Mt7cItem;
    }

    // ---------------------------------------------------------------- mode change

    public static void switchMode(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof Mt7cItem) || p.isSpectator() || BREACHES.containsKey(p.getUUID())) return;
        if (p.getCooldowns().isOnCooldown(stack.getItem())) return;
        if (p.isUsingItem() && p.getUseItem().getItem() instanceof Mt7cItem) p.stopUsingItem();   // a charge is dropped, not fired
        int from = Mt7cItem.mode(stack), to = (from + 1) % 3;
        Mt7cItem.setMode(stack, to);
        p.getCooldowns().addCooldown(stack.getItem(), Mt7cItem.SWITCH_TICKS);
        sound(p.serverLevel(), p.position(), ModSounds.MT7C_SWITCH.get(), 0.7F, 1.0F);
        send(p, Mt7cAnimPacket.SWITCH, from, to);
    }

    // ---------------------------------------------------------------- the thrust

    /** Use let go after charging for {@code charged} ticks. */
    public static void release(ServerPlayer p, ItemStack used, int charged) {
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof Mt7cItem) || BREACHES.containsKey(p.getUUID())) return;
        ServerLevel level = p.serverLevel();
        int mode = Mt7cItem.mode(stack);
        int depth = Mt7cItem.depthFor(mode, charged);
        if (depth <= 0) return;                                                  // a tap: nothing happens
        List<List<BlockPos>> layers = Mt7cBreach.plan(level, p, mode, depth);
        int planned = Mt7cBreach.count(layers);
        Vec3 hand = p.getEyePosition().add(p.getViewVector(1.0F).scale(1.2D));
        boolean creative = p.getAbilities().instabuild;
        net.minecraft.world.item.Item item = stack.getItem();
        if (!creative) {
            stack.hurtAndBreak(Mt7cItem.THRUST_DURABILITY, p, e -> e.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.MAINHAND));
            p.causeFoodExhaustion(Mt7cItem.THRUST_EXHAUSTION);
            if (stack.isEmpty()) return;                                         // that thrust was its last
        }
        if (planned == 0) {                                                      // a whiff: the piston fires on air
            p.getCooldowns().addCooldown(item, Mt7cItem.VENT_WHIFF);
            sound(level, hand, ModSounds.MT7C_IMPACT.get(), 0.4F, 1.5F);
            send(p, Mt7cAnimPacket.THRUST, 0, mode);
            return;
        }
        int vent = Math.min(Mt7cItem.VENT_MAX, Mt7cItem.VENT_BASE + Mt7cItem.VENT_PER_BLOCK * planned);
        p.getCooldowns().addCooldown(item, vent);
        long now = level.getGameTime();
        BREACHES.put(p.getUUID(), new Breach(layers, level.dimension(), mode, p.getEyePosition(), now + IMPACT_DELAY, now + vent));
        BlockPos first = layers.get(0).get(0);
        sound(level, Vec3.atCenterOf(first), ModSounds.MT7C_IMPACT.get(), 1.0F, 0.95F + 0.1F * level.random.nextFloat());
        send(p, Mt7cAnimPacket.THRUST, planned, mode);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Breach b = BREACHES.get(p.getUUID());
        if (b == null) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (now < b.nextTick) return;
        if (!holding(p) || b.dim != level.dimension() || !p.isAlive()) {         // put away, broke, or gone: the breach stops
            BREACHES.remove(p.getUUID());
            return;
        }
        List<BlockPos> layer = b.layers.get(b.next);
        int took = 0;
        boolean stop = false;
        Vec3 centre = Vec3.ZERO;
        for (BlockPos pos : layer) {
            if (!Mt7cBreach.breakable(level, pos, b.mode)) {
                if (b.mode == Mt7cItem.PICK) stop = true;                        // the world changed under the tunnel
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!level.mayInteract(p, pos) || !p.gameMode.destroyBlock(pos)) {                                 // a claim, spawn protection, an event said no
                if (b.mode == Mt7cItem.PICK) stop = true;
                continue;
            }
            took++;
            Vec3 c = Vec3.atCenterOf(pos);
            centre = centre.add(c);
            debris(level, c, state, b.from);
            if (!holding(p)) {                                                   // the tool just broke
                stop = true;
                break;
            }
        }
        b.broken += took;
        if (took > 0) {
            centre = centre.scale(1.0D / took);
            float pitch = 0.9F + 0.15F * level.random.nextFloat() - 0.05F * b.next;
            sound(level, centre, ModSounds.MT7C_CRACK.get(), 0.75F + 0.05F * took, pitch);
            if (!p.getAbilities().instabuild) p.causeFoodExhaustion(Mt7cItem.BLOCK_EXHAUSTION * took);
            send(p, Mt7cAnimPacket.LAYER, b.next, took);
        }
        b.next++;
        b.nextTick = now + 1;
        if (stop || b.next >= b.layers.size()) {
            BREACHES.remove(p.getUUID());
            vent(p, level, (int) Math.max(10L, b.ventUntil - now));
        }
    }

    /** Impact debris: chips of the block thrown back toward the tool, a puff of dust. */
    private static void debris(ServerLevel level, Vec3 at, BlockState state, Vec3 from) {
        Vec3 back = from.subtract(at).normalize();
        BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, state);
        level.sendParticles(chips, at.x, at.y, at.z, 14, 0.3D, 0.3D, 0.3D, 0.15D);
        for (int i = 0; i < 4; i++) {
            double sx = (level.random.nextDouble() - 0.5D) * 0.5D, sy = level.random.nextDouble() * 0.3D, sz = (level.random.nextDouble() - 0.5D) * 0.5D;
            level.sendParticles(chips, at.x, at.y, at.z, 0, back.x + sx, back.y + 0.2D + sy, back.z + sz, 0.35D);
        }
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 3, 0.25D, 0.25D, 0.25D, 0.02D);
    }

    /** The recovery: compressed air out of the vents (the item cooldown is already running). */
    private static void vent(ServerPlayer p, ServerLevel level, int ticks) {
        Vec3 at = p.getEyePosition().add(p.getViewVector(1.0F).scale(0.6D)).subtract(0.0D, 0.35D, 0.0D);
        sound(level, at, ModSounds.MT7C_VENT.get(), 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.08D, 0.05D, 0.08D, 0.05D);
        send(p, Mt7cAnimPacket.VENT, ticks, 0);
    }

    // ---------------------------------------------------------------- quiet

    /**
     * The MT-7C makes no vibration: blocks it breaks, paths and tilled soil it makes, and the charge and release of a
     * breach are all silent to sculk sensors, shriekers and the Warden.
     */
    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        GameEvent e = event.getVanillaEvent();
        if (e != GameEvent.BLOCK_DESTROY && e != GameEvent.BLOCK_CHANGE && e != GameEvent.ITEM_INTERACT_START
                && e != GameEvent.ITEM_INTERACT_FINISH) return;
        if (event.getCause() instanceof Player p && holding(p)) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- cleanup

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BREACHES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BREACHES.clear();
    }
}
