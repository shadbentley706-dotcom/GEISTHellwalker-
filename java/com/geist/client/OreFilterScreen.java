package com.geist.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ore Filter: click the ores Ore Pulse should show. ALL / NONE / DONE along the bottom. */
public class OreFilterScreen extends Screen {
    private static final ItemStack[] ICONS = {new ItemStack(Items.ANCIENT_DEBRIS), new ItemStack(Items.DIAMOND_ORE),
            new ItemStack(Items.EMERALD_ORE), new ItemStack(Items.GOLD_ORE), new ItemStack(Items.LAPIS_ORE),
            new ItemStack(Items.REDSTONE_ORE), new ItemStack(Items.IRON_ORE), new ItemStack(Items.COPPER_ORE),
            new ItemStack(Items.COAL_ORE), new ItemStack(Items.NETHER_QUARTZ_ORE), new ItemStack(Items.RAW_IRON_BLOCK)};
    private static final int COLS = 4, CELL = 44;
    private float open;

    public OreFilterScreen() {
        super(Component.translatable("screen.geist.ore_filter"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int gridX() { return this.width / 2 - COLS * CELL / 2; }
    private int gridY() { return this.height / 2 - 70; }

    private int cellAt(double mx, double my) {
        int cx = (int) Math.floor((mx - gridX()) / CELL), cy = (int) Math.floor((my - gridY()) / CELL);
        if (cx < 0 || cx >= COLS || cy < 0) return -1;
        int i = cy * COLS + cx;
        return i < OrePulse.NAMES.length ? i : -1;
    }

    private int buttonAt(double mx, double my) {
        int by = gridY() + 3 * CELL + 8;
        if (my < by || my > by + 14) return -1;
        int bx = this.width / 2 - 90;
        for (int i = 0; i < 3; i++) if (mx >= bx + i * 62 && mx <= bx + i * 62 + 56) return i;
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        open = Math.min(1.0F, open + 0.15F);
        Font font = this.font;
        g.fill(0, 0, this.width, this.height, Holo.argb(0.45F * open, 0x020A12));
        int gx = gridX(), gy = gridY();
        String title = "ORE FILTER // SELECT SIGNATURES";
        Holo.glowText(g, font, title, this.width / 2.0F - Holo.width(font, title, 0.8F) / 2.0F, gy - 18, 0.8F, Holo.NEON, open);
        int hover = cellAt(mouseX, mouseY);
        for (int i = 0; i < OrePulse.NAMES.length; i++) {
            int x = gx + (i % COLS) * CELL, y = gy + (i / COLS) * CELL;
            boolean on = OrePulse.FILTER[i], hov = i == hover;
            int col = OrePulse.COLORS[i];
            g.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, Holo.argb((on ? 0.45F : 0.2F) * open, on ? 0x0E3A5A : 0x0A1620));
            Holo.frame(g, x + 2, y + 2, x + CELL - 2, y + CELL - 2, 3, on ? col : 0x506070, (hov ? 1.0F : 0.75F) * open, 1.0F);
            g.pose().pushPose();
            g.pose().translate(x + CELL / 2.0F - 10, y + 6, 0.0F);
            g.pose().scale(1.25F, 1.25F, 1.0F);
            g.renderItem(ICONS[i], 0, 0);
            g.pose().popPose();
            if (!on) g.fill(x + 4, y + 4, x + CELL - 4, y + CELL - 4, 0x90060A10);   // dim what's hidden
            String n = OrePulse.NAMES[i];
            Holo.text(g, font, n, x + CELL / 2.0F - Holo.width(font, n, 0.55F) / 2.0F, y + 29, 0.55F, Holo.argb(open, on ? col : 0x708090));
            g.fill(x + CELL - 9, y + 5, x + CELL - 5, y + 9, on ? 0xFF4CFF7A : 0xFFFF3B3B);
        }
        String[] labels = {"ALL", "NONE", "DONE"};
        int bh = buttonAt(mouseX, mouseY), bx = this.width / 2 - 90, by = gy + 3 * CELL + 8;
        for (int i = 0; i < 3; i++) {
            int x = bx + i * 62;
            g.fill(x, by, x + 56, by + 14, Holo.argb((bh == i ? 0.6F : 0.35F) * open, 0x0E2E4A));
            Holo.frame(g, x, by, x + 56, by + 14, 2, Holo.NEON, (bh == i ? 1.0F : 0.7F) * open, 1.0F);
            Holo.text(g, font, labels[i], x + 28 - Holo.width(font, labels[i], 0.7F) / 2.0F, by + 4, 0.7F, Holo.argb(open, Holo.WHITE));
        }
        String hint = "CLICK AN ORE TO SHOW / HIDE IT";
        Holo.text(g, font, hint, this.width / 2.0F - Holo.width(font, hint, 0.55F) / 2.0F, by + 20, 0.55F, Holo.argb(0.8F * open, Holo.NEON));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int c = cellAt(mouseX, mouseY);
        if (c >= 0 && minecraft != null && minecraft.player != null) {
            OrePulse.FILTER[c] = !OrePulse.FILTER[c];
            minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, OrePulse.FILTER[c] ? 1.8F : 1.0F);
            return true;
        }
        int b = buttonAt(mouseX, mouseY);
        if (b == 0 || b == 1) {
            java.util.Arrays.fill(OrePulse.FILTER, b == 0);
            if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 1.4F);
            return true;
        }
        if (b == 2) { this.onClose(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
