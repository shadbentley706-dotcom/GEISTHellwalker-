package com.geist.client;

import com.geist.GeistMod;
import com.geist.network.GeistNetwork;
import com.geist.network.PeekRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * X-Ray Chest Peek: look at a chest, shulker box, hopper, barrel (any container) within 12 blocks and a
 * holographic panel shows its fill percentage and a mini-grid of its items, without opening it.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class ChestPeek {
    private static final double RANGE = 12.0D;
    @Nullable private static BlockPos looking;          // container under the crosshair right now
    @Nullable private static BlockPos dataPos;          // container the data below belongs to
    private static int size;
    private static boolean sealed;
    private static final List<Integer> SLOTS = new ArrayList<>();
    private static final List<ItemStack> ITEMS = new ArrayList<>();
    private static long received;

    private ChestPeek() {}

    public static void receive(BlockPos pos, int containerSize, boolean isSealed, List<Integer> slots, List<ItemStack> items) {
        dataPos = pos;
        size = containerSize;
        sealed = isSealed;
        SLOTS.clear(); SLOTS.addAll(slots);
        ITEMS.clear(); ITEMS.addAll(items);
        Minecraft mc = Minecraft.getInstance();
        received = mc.level != null ? mc.level.getGameTime() : 0;
    }

    @Nullable
    private static BlockPos target(Minecraft mc) {
        HitResult hit = mc.player.pick(RANGE, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        return be instanceof Container ? pos : null;
    }

    /** Ask the server for the contents a few times a second while you look at a container. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !GeistState.module(mc, GeistState.PEEK)) { looking = null; return; }
        BlockPos t = target(mc);
        boolean changed = t != null && !t.equals(looking);
        looking = t;
        if (t != null && (changed || mc.level.getGameTime() % 8 == 0)) GeistNetwork.CHANNEL.sendToServer(new PeekRequestPacket(t));
    }

    /** Is the x-ray panel showing (you are looking at a container)? */
    static boolean active(Minecraft mc) {
        return looking != null && GeistState.module(mc, GeistState.PEEK);
    }

    static void render(GuiGraphics g, Minecraft mc, int sw, int sh, float time) {
        if (looking == null || !GeistState.module(mc, GeistState.PEEK)) return;
        Font font = mc.font;
        boolean fresh = looking.equals(dataPos) && mc.level.getGameTime() - received < 40;
        int cols = 9, rows = Math.max(1, Mth.ceil((fresh ? size : 27) / 9.0F));
        int slot = 11, gw = cols * slot, w = gw + 10, h = 30 + rows * slot + 4;
        int x = sw / 2 + 26, y = sh / 2 - h / 2;
        if (x + w > sw - 4) x = sw / 2 - 26 - w;
        g.fill(x, y, x + w, y + h, Holo.argb(0.6F, 0x04121C));
        Holo.frame(g, x, y, x + w, y + h, 3, Holo.NEON, 0.9F, 1.0F);
        Holo.line(g, sw / 2 + 6, sh / 2, x, y + 8, Holo.argb(0.6F, Holo.NEON));             // leader line
        String name = mc.level.getBlockState(looking).getBlock().getName().getString().toUpperCase();
        Holo.glowText(g, font, "X-RAY // " + name, x + 5, y + 4, 0.6F, Holo.NEON, 1.0F);
        if (!fresh) {
            String dots = ".".repeat(1 + (int) (time / 5) % 3);
            Holo.text(g, font, "SCANNING" + dots, x + 5, y + 16, 0.7F, Holo.argb(0.9F, Holo.WHITE));
            return;
        }
        if (sealed) {
            Holo.glowText(g, font, "SEALED", x + 5, y + 14, 0.8F, Holo.AMBER, 1.0F);
            Holo.text(g, font, "LOOT NOT GENERATED", x + 5, y + 23, 0.55F, Holo.argb(0.9F, Holo.WHITE));
            return;
        }
        // fill: like a comparator, how full the slots are (stack fullness), plus how many slots are used
        float fullness = 0.0F;
        for (ItemStack s : ITEMS) fullness += s.getCount() / (float) Math.max(1, s.getMaxStackSize());
        int pct = size == 0 ? 0 : Math.round(fullness / size * 100.0F);
        Holo.glowText(g, font, "FILL " + pct + "%", x + 5, y + 13, 0.75F, pct >= 90 ? Holo.RED : (pct >= 50 ? Holo.AMBER : Holo.GREEN), 1.0F);
        String used = "SLOTS " + ITEMS.size() + "/" + size;
        Holo.text(g, font, used, x + w - 5 - Holo.width(font, used, 0.55F), y + 14, 0.55F, Holo.argb(0.9F, Holo.WHITE));
        int bx = x + 5, by = y + 22, bw = w - 10;
        g.fill(bx, by, bx + bw, by + 2, Holo.argb(0.3F, Holo.NEON));
        g.fill(bx, by, bx + Math.round(bw * Math.min(1.0F, pct / 100.0F)), by + 2, Holo.argb(1.0F, Holo.NEON));
        // the mini-grid
        int gx = x + 5, gy = y + 28;
        for (int i = 0; i < size; i++) {
            int sx = gx + (i % cols) * slot, sy = gy + (i / cols) * slot;
            g.fill(sx, sy, sx + slot - 1, sy + slot - 1, Holo.argb(0.25F, Holo.NEON));
        }
        for (int i = 0; i < ITEMS.size(); i++) {
            int s = SLOTS.get(i);
            if (s >= size) continue;
            int sx = gx + (s % cols) * slot, sy = gy + (s / cols) * slot;
            g.pose().pushPose();
            g.pose().translate(sx + 0.5F, sy + 0.5F, 0.0F);
            g.pose().scale(0.6F, 0.6F, 1.0F);
            g.renderItem(ITEMS.get(i), 0, 0);
            g.renderItemDecorations(font, ITEMS.get(i), 0, 0);
            g.pose().popPose();
        }
    }
}
