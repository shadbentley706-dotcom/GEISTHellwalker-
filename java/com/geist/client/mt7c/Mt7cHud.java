package com.geist.client.mt7c;

import com.geist.GeistMod;
import com.geist.client.Holo;
import com.geist.client.WorldLines;
import com.geist.item.Mt7cBreach;
import com.geist.item.Mt7cItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The MT-7C readout beside the crosshair (mode, status LED, breach depth meter while charging, venting, integrity)
 * and, while charging, the blocks the breach will take outlined in the world (the next depth step fainter).
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class Mt7cHud {
    public static final IGuiOverlay OVERLAY = (gui, g, pt, w, h) -> render(g, pt, w, h);
    private static final int OD = 0xB8C27A, LIT = 0x7CFF6A, AMBER = 0xFFB02E, RED = 0xFF4A3A;

    private Mt7cHud() {}

    @Mod.EventBusSubscriber(modid = GeistMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("mt7c_hud", OVERLAY);
        }
    }

    private static void render(GuiGraphics g, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui || !Mt7cClient.holding(p)) return;
        ItemStack stack = p.getMainHandItem();
        Font font = mc.font;
        float t = mc.level.getGameTime() + pt;
        int mode = Mt7cItem.mode(stack);
        Mt7cClient.Anim a = Mt7cClient.peek(p.getId());
        float charge = Mt7cAnim.charge(p, pt);
        boolean cooling = p.getCooldowns().isOnCooldown(stack.getItem());
        float coolLeft = p.getCooldowns().getCooldownPercent(stack.getItem(), pt);
        boolean switching = a != null && t - a.switchAt >= 0.0F && t - a.switchAt < Mt7cItem.SWITCH_TICKS;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int lh = Holo.lineH(Holo.SMALL);
        float x = sw / 2.0F + 12, y = sh / 2.0F + 6;
        Holo.say(g, font, "MT-7C // " + Mt7cItem.MODE_NAMES[mode], x, y, Holo.SMALL, Holo.argb(0.95F, OD));
        y += lh;
        String status;
        int col;
        float led = 1.0F;
        if (switching) {
            status = "RECONFIGURING";
            col = AMBER;
            led = Mth.sin(t * 1.6F) > 0.0F ? 1.0F : 0.2F;
        } else if (charge >= 0.0F) {
            int depth = Mt7cItem.depthFor(mode, charge);
            status = depth == 0 ? "CHARGING" : mode == Mt7cItem.PICK ? "BREACH " + depth + "M TUNNEL" : "BREACH 3x3 x" + depth;
            col = charge >= Mt7cItem.FULL_CHARGE ? RED : AMBER;
            if (charge >= Mt7cItem.FULL_CHARGE) led = Mth.sin(t * 2.2F) > 0.0F ? 1.0F : 0.25F;
        } else if (cooling) {
            status = "VENTING";
            col = AMBER;
            led = Mth.sin(t * 0.5F) > 0.0F ? 1.0F : 0.35F;
        } else {
            status = "LOCKED // READY";
            col = LIT;
        }
        g.fill(Math.round(x), Math.round(y + 1), Math.round(x) + 3, Math.round(y) + 4, Holo.argb(led, col));
        Holo.say(g, font, status, x + 5, y, Holo.SMALL, Holo.argb(0.95F, col));
        y += lh;

        if (charge >= 0.0F) {                                                    // depth meter: one segment per step
            int steps = Mt7cItem.maxDepth(mode), depth = Mt7cItem.depthFor(mode, charge);
            float frac = Mth.clamp(charge / Mt7cItem.FULL_CHARGE, 0.0F, 1.0F);
            int segW = 9, gap = 2;
            for (int i = 0; i < steps; i++) {
                int sx = Math.round(x) + i * (segW + gap), sy = Math.round(y) + 1;
                int c = i < depth ? (charge >= Mt7cItem.FULL_CHARGE ? RED : AMBER) : 0x2A2E24;
                g.fill(sx, sy, sx + segW, sy + 3, Holo.argb(i < depth ? 0.95F : 0.6F, c));
            }
            Holo.bar(g, Math.round(x), Math.round(y) + 6, steps * (segW + gap) - gap, 1, frac, Holo.argb(0.8F, OD), Holo.argb(0.4F, 0x101208));
            y += 10;
        } else if (cooling) {
            Holo.bar(g, Math.round(x), Math.round(y) + 1, 50, 2, 1.0F - coolLeft, Holo.argb(0.9F, AMBER), Holo.argb(0.45F, 0x101208));
            y += 6;
        }
        int max = stack.getMaxDamage();
        float integrity = max > 0 ? 1.0F - stack.getDamageValue() / (float) max : 1.0F;
        Holo.say(g, font, "INTEGRITY " + Math.round(integrity * 100) + "%", x, y, Holo.SMALL,
                Holo.argb(0.7F, integrity < 0.15F ? RED : 0x9AA08A));
        y += lh;
        if (charge < 0.0F && !switching) {
            String key = Mt7cClient.MODE_KEY.getTranslatedKeyMessage().getString();
            Holo.say(g, font, "[" + key + "] MODE  /  HOLD USE: BREACH", x, y, Holo.SMALL, Holo.argb(0.4F, 0xC8CCBC));
        }
        RenderSystem.disableBlend();
    }

    /** While charging: the blocks the breach will take, outlined; the next depth step fainter. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !Mt7cClient.holding(p)) return;
        float charge = Mt7cAnim.charge(p, event.getPartialTick());
        if (charge < 0.0F) return;
        int mode = Mt7cItem.mode(p.getMainHandItem());
        int depth = Math.max(1, Mt7cItem.depthFor(mode, charge));
        List<List<BlockPos>> now = Mt7cBreach.plan(mc.level, p, mode, depth);
        List<List<BlockPos>> next = depth < Mt7cItem.maxDepth(mode) ? Mt7cBreach.plan(mc.level, p, mode, depth + 1) : now;
        if (next.isEmpty()) return;
        boolean armed = Mt7cItem.depthFor(mode, charge) > 0;
        boolean full = charge >= Mt7cItem.FULL_CHARGE;
        float t = mc.level.getGameTime() + event.getPartialTick();
        int rgb = full ? RED : armed ? AMBER : OD;
        float pulse = 0.75F + 0.25F * Mth.sin(t * (full ? 1.2F : 0.5F));
        Set<BlockPos> taken = new HashSet<>();
        for (List<BlockPos> l : now) taken.addAll(l);
        PoseStack ps = event.getPoseStack();
        BufferBuilder b = WorldLines.begin(event.getCamera().getPosition(), false, 2.0F);
        for (int i = 0; i < next.size(); i++) {
            for (BlockPos pos : next.get(i)) {
                AABB box = new AABB(pos).inflate(0.003D);
                if (taken.contains(pos) && armed) {
                    WorldLines.box(ps, b, box, rgb, pulse * (1.0F - 0.1F * i));
                } else {
                    WorldLines.box(ps, b, box, OD, 0.22F);
                }
            }
        }
        WorldLines.end();
    }
}
