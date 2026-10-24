package com.geist.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * The G.E.I.S.T. command wheel. Hold X, point at a segment, release X (or click) to use it.
 * Scroll the mouse wheel to change page. Keys 1-N use a segment directly.
 * Ore Pulse: release X fires it; left-click opens the Ore Filter.
 */
public class CommandWheelScreen extends Screen {
    private static final String[] KEYS = {"scan", "deep", "peek", "nav", "portal", "ore", "vitals", "chrono", "trajectory", "filter"};
    private static final ItemStack[] ICONS = {new ItemStack(Items.SPYGLASS), new ItemStack(Items.ENDER_EYE),
            new ItemStack(Items.CHEST), new ItemStack(Items.COMPASS), new ItemStack(Items.OBSIDIAN), new ItemStack(Items.DIAMOND_ORE),
            new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.CLOCK), new ItemStack(Items.BOW), new ItemStack(Items.IRON_ORE)};
    /** Module ids on each page. */
    private static final int[][] PAGES = {
            {GeistState.SCAN, GeistState.DEEP, GeistState.PEEK, GeistState.NAV, GeistState.PORTAL, GeistState.ORE},
            {GeistState.VITALS, GeistState.CHRONO, GeistState.TRAJECTORY, GeistState.FILTER}};
    private static int page;                                   // remembered between openings
    private static final float INNER = 36.0F, OUTER = 84.0F, GAP = 3.5F;
    private int hovered = -1;                                  // index on the current page
    private float open, flip = 1.0F;

    public CommandWheelScreen() {
        super(Component.translatable("screen.geist.wheel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isAction(int id) {
        return id == GeistState.ORE || id == GeistState.FILTER;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        open = Math.min(1.0F, open + 0.12F);
        flip = Math.min(1.0F, flip + 0.15F);
        float e = open * open * (3.0F - 2.0F * open) * (0.6F + 0.4F * flip);
        Font font = this.font;
        int[] ids = PAGES[page];
        int n = ids.length;
        float seg = 360.0F / n;
        float cx = this.width / 2.0F, cy = this.height / 2.0F;
        g.fill(0, 0, this.width, this.height, Holo.argb(0.35F * open, 0x020A12));

        float dx = mouseX - cx, dy = mouseY - cy;
        hovered = -1;
        if (dx * dx + dy * dy > 14 * 14) {
            float ang = (float) Math.toDegrees(Math.atan2(dx, -dy));
            if (ang < 0) ang += 360.0F;
            hovered = Mth.clamp((int) (ang / seg), 0, n - 1);
        }
        float time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() + partialTick : 0;

        g.flush();
        for (int i = 0; i < n; i++) {
            boolean hov = i == hovered;
            float a0 = i * seg + GAP, a1 = (i + 1) * seg - GAP, mid = (a0 + a1) / 2.0F;
            float push = hov ? 6.0F : 0.0F;
            float ox = (float) Math.sin(Math.toRadians(mid)) * push, oy = (float) -Math.cos(Math.toRadians(mid)) * push;
            sector(g, cx + ox, cy + oy, INNER * e, (OUTER + (hov ? 6.0F : 0.0F)) * e, a0, a1,
                    hov ? 0x2A7FC0 : 0x0E2E4A, hov ? 0.85F : 0.62F, hov ? 0x55C8FF : 0x1C5A86, hov ? 0.9F : 0.7F);
        }
        RenderSystem.disableBlend();

        for (int i = 0; i < n; i++) {
            int id = ids[i];
            boolean hov = i == hovered, on = GeistState.MODULES[id];
            float a0 = i * seg + GAP, a1 = (i + 1) * seg - GAP, mid = (a0 + a1) / 2.0F;
            float push = hov ? 6.0F : 0.0F;
            float ox = (float) Math.sin(Math.toRadians(mid)) * push, oy = (float) -Math.cos(Math.toRadians(mid)) * push;
            float scx = cx + ox, scy = cy + oy, r1 = (OUTER + (hov ? 6.0F : 0.0F)) * e;
            Holo.arc(g, scx, scy, r1, a0, a1, Holo.argb((hov ? 1.0F : 0.6F) * e, hov ? Holo.WHITE : Holo.NEON));
            Holo.arc(g, scx, scy, INNER * e, a0, a1, Holo.argb(0.5F * e, Holo.NEON));
            if (e < 0.85F) continue;
            float rr = (INNER + OUTER) / 2.0F + (hov ? 3.0F : 0.0F);
            float ix = scx + (float) Math.sin(Math.toRadians(mid)) * rr, iy = scy - (float) Math.cos(Math.toRadians(mid)) * rr;
            g.pose().pushPose();
            g.pose().translate(ix - (hov ? 12.0F : 10.0F), iy - (hov ? 16.0F : 14.0F), 0.0F);
            float sc = hov ? 1.5F : 1.25F;
            g.pose().scale(sc, sc, 1.0F);
            g.renderItem(ICONS[id], 0, 0);
            g.pose().popPose();
            String name = Component.translatable("module.geist." + KEYS[id]).getString();
            Holo.sayCentered(g, font, name, ix, iy + 9, Holo.SMALL, 0xFF000000 | (hov ? Holo.WHITE : Holo.NEON));
            int light = id == GeistState.ORE ? (OrePulse.cooldownLeft(minecraft) > 0 ? 0xFFFFC14D : 0xFF3FD3FF)
                    : id == GeistState.FILTER ? 0xFF3FD3FF : (on ? 0xFF4CFF7A : 0xFFFF3B3B);
            g.fill(Math.round(ix + 13), Math.round(iy - 13), Math.round(ix + 16), Math.round(iy - 10), light);
            float nx = scx + (float) Math.sin(Math.toRadians(a0 + 8)) * (r1 - 7), ny = scy - (float) Math.cos(Math.toRadians(a0 + 8)) * (r1 - 7);
            Holo.say(g, font, String.valueOf(i + 1), nx - 2, ny - 3, Holo.SMALL, Holo.argb(0.9F, Holo.WHITE));
        }

        Holo.arc(g, cx, cy, (OUTER + 14) * e, time * 1.5F, time * 1.5F + 140, Holo.argb(0.45F * e, Holo.NEON));
        Holo.arc(g, cx, cy, (OUTER + 14) * e, time * 1.5F + 180, time * 1.5F + 260, Holo.argb(0.3F * e, Holo.NEON));
        Holo.arc(g, cx, cy, (OUTER + 20) * e, -time, -time + 60, Holo.argb(0.25F * e, Holo.NEON));
        Holo.arc(g, cx, cy, (INNER - 4) * e, 0, 360, Holo.argb(0.8F * e, Holo.NEON));
        if (e < 0.85F) return;

        // the core
        if (hovered >= 0) {
            int id = ids[hovered];
            String name = Component.translatable("module.geist." + KEYS[id]).getString();
            String st, hint = "LET GO TO", act;
            int stc;
            if (id == GeistState.ORE) {
                float cd = OrePulse.cooldownLeft(minecraft);
                st = cd > 0 ? String.format("WAIT %.0fS", cd) : "READY";
                stc = cd > 0 ? Holo.AMBER : Holo.NEON;
                act = "PULSE";
            } else if (id == GeistState.FILTER) {
                int shown = 0;
                for (boolean b : OrePulse.FILTER) if (b) shown++;
                st = shown + "/" + OrePulse.FILTER.length + " ORES";
                stc = Holo.NEON;
                act = "OPEN";
            } else {
                boolean on = GeistState.MODULES[id];
                st = on ? "ON" : "OFF";
                stc = on ? Holo.GREEN : Holo.RED;
                act = on ? "TURN OFF" : "TURN ON";
            }
            int ls = Holo.lineH(Holo.SMALL), fitW = (int) (INNER * 2) - 8;
            Holo.sayCentered(g, font, Holo.fit(font, st, Holo.SMALL, fitW), cx, cy - ls * 1.5F - 1, Holo.SMALL, 0xFF000000 | stc);
            Holo.sayCentered(g, font, hint, cx, cy - ls * 0.5F, Holo.SMALL, Holo.argb(0.75F, Holo.NEON));
            Holo.sayCentered(g, font, Holo.fit(font, act, Holo.SMALL, fitW), cx, cy + ls * 0.5F, Holo.SMALL, 0xFF000000 | Holo.WHITE);
            String desc = name + "  \u00B7  " + Component.translatable("module.geist." + KEYS[id] + ".desc").getString();
            float descY = cy + OUTER + 22;
            int dw = Holo.textW(font, desc, Holo.MEDIUM) + 12;
            g.fill(Math.round(cx - dw / 2.0F), Math.round(descY - 3), Math.round(cx + dw / 2.0F), Math.round(descY + Holo.lineH(Holo.MEDIUM) + 2), Holo.argb(0.6F, 0x03121B));
            Holo.sayCentered(g, font, desc, cx, descY, Holo.MEDIUM, 0xFFFFFFFF);
        } else {
            String s = "G.E.I.S.T.";
            Holo.sayCentered(g, font, s, cx, cy - Holo.lineH(Holo.MEDIUM) + 1, Holo.MEDIUM, 0xFF000000 | Holo.NEON);
            String c = "PAGE " + (page + 1) + "/" + PAGES.length;
            Holo.sayCentered(g, font, c, cx, cy + 2, Holo.SMALL, Holo.argb(0.9F, Holo.WHITE));
        }
        String help = Component.translatable("screen.geist.wheel.help").getString();
        Holo.sayCentered(g, font, help, cx, cy - OUTER - 34, Holo.SMALL, Holo.argb(0.9F, Holo.NEON));
        // page dots
        for (int p = 0; p < PAGES.length; p++) {
            int px = Math.round(cx - (PAGES.length - 1) * 5 + p * 10), py = Math.round(cy + OUTER + 40);
            g.fill(px - 2, py - 2, px + 2, py + 2, p == page ? 0xFF3FD3FF : 0x603FD3FF);
        }
    }

    private static void sector(GuiGraphics g, float cx, float cy, float r0, float r1, float a0, float a1,
                               int innerRgb, float innerA, int outerRgb, float outerA) {
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / steps);
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            bb.vertex(m, cx + sx * r1, cy + sy * r1, 0.0F).color((outerRgb >> 16) & 0xFF, (outerRgb >> 8) & 0xFF, outerRgb & 0xFF, (int) (outerA * 255)).endVertex();
            bb.vertex(m, cx + sx * r0, cy + sy * r0, 0.0F).color((innerRgb >> 16) & 0xFF, (innerRgb >> 8) & 0xFF, innerRgb & 0xFF, (int) (innerA * 255)).endVertex();
        }
        tess.end();
    }

    /** Use segment i of the current page. Returns true if the wheel should close. */
    private boolean use(int i, boolean click) {
        int[] ids = PAGES[page];
        if (i < 0 || i >= ids.length || minecraft == null || minecraft.player == null) return true;
        int id = ids[i];
        if (id == GeistState.FILTER || (id == GeistState.ORE && click)) {          // open the Ore Filter
            minecraft.setScreen(new OreFilterScreen());
            return false;
        }
        if (id == GeistState.ORE) {
            OrePulse.pulse(minecraft);
            return true;
        }
        GeistState.MODULES[id] = !GeistState.MODULES[id];
        boolean on = GeistState.MODULES[id];
        minecraft.player.displayClientMessage(Component.translatable("module.geist." + KEYS[id])
                .append(Component.literal(on ? " // ONLINE" : " // OFFLINE"))
                .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY), true);
        minecraft.player.playSound(on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.35F, 1.9F);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (GeistState.WHEEL.matches(keyCode, scanCode)) {
            if (use(hovered, false)) this.onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int n = PAGES[page].length;
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + n) {
            if (!use(keyCode - GLFW.GLFW_KEY_1, false)) return true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0) {
            if (use(hovered, true)) this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Scroll to flip between pages. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        page = Math.floorMod(page + (delta < 0 ? 1 : -1), PAGES.length);
        flip = 0.0F;
        if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, 1.5F + page * 0.2F);
        return true;
    }
}
