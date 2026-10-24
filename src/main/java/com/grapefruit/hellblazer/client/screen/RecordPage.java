package com.grapefruit.hellblazer.client.screen;

import com.grapefruit.hellblazer.client.CaseData;
import com.grapefruit.hellblazer.client.CaseText;
import com.grapefruit.hellblazer.client.ui.Grim;
import com.grapefruit.hellblazer.client.ui.Hud;
import com.grapefruit.hellblazer.client.ui.Ui;
import com.grapefruit.hellblazer.occult.CaseType;
import com.grapefruit.hellblazer.occult.HbRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * RECORD: the specialist's standing with the section - the rank, the figure rolling up to the next one, what's been
 * taken, closed, lost and walked away from - beside the ladder of ranks (what each one opens), and the field notes:
 * what each kind of file asks of you and how it's done.
 */
final class RecordPage implements Page {
    private static final String[] STATS = {"accepted", "closed", "failed", "abandoned", "targets", "kills"};
    private float shownAt, scroll, scrollTo;
    private int ladX0, ladY0, ladX1, ladY1, ladH;

    @Override
    public void shown(float t) {
        shownAt = t;
    }

    @Override
    public void tick() {
        scroll += (scrollTo - scroll) * 0.45F;
    }

    @Override
    public boolean scroll(double x, double y, double delta) {
        if (x < ladX0 || x >= ladX1 || y < ladY0 || y >= ladY1) return false;
        scrollTo = Mth.clamp(scrollTo - (float) delta * 16.0F, 0.0F, Math.max(0.0F, ladH - (ladY1 - ladY0)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, Ui ui, CasebookScreen screen, int x0, int y0, int x1, int y1, float a) {
        int split = x0 + Math.round((x1 - x0) * 0.46F);
        standing(g, ui, x0, y0, split - 3, y1, a);
        ladder(g, ui, split + 3, y0, x1, y1, a);
    }

    /** The specialist: rank, the figure, the bar to the next rank, the tallies. */
    private void standing(GuiGraphics g, Ui ui, int x0, int y0, int x1, int y1, float a) {
        Grim.panel(g, x0, y0, x1, y1, Grim.RED, a);
        int lh = ui.lh();
        int y = y0 + 4;
        y += Grim.header(g, ui.font, CaseText.tr("casebook.geist_hellblazer.record.standing"), x0 + 3, y, x1 - 4, Grim.RED, a);
        Minecraft mc = Minecraft.getInstance();
        String name = mc.player != null ? mc.player.getGameProfile().getName() : "";
        HbRank r = CaseData.rank();
        HbRank next = r.next();
        float since = ui.t - shownAt;
        // the sigil, small, beside the rank
        int sh = Hud.lineH(Ui.L) + Hud.lineH(Ui.S) + 4;
        Grim.sigil(g, x0 + 6, y + 1, sh * 0.5F, sh, Grim.RED, a);
        int tx = x0 + 10 + Math.round(sh * 0.5F);
        ui.say(g, name, tx, y + 1, Grim.GREY, a);
        Grim.fringe(g, ui.font, Grim.bleed(r.title, since * 1.6F, 21), tx, y + 2 + lh, Ui.L, Grim.RED, a);
        y += sh + 6;
        int rep = CaseData.shownRep();
        ui.say(g, CaseText.tr("casebook.geist_hellblazer.record.rep"), x0 + 6, y, Grim.DIM, a);
        ui.sayRight(g, String.format(Locale.ROOT, "%,d", rep), x1 - 6, y, Grim.BONE, a);
        y += lh + 1;
        float f = next == null ? 1.0F : Mth.clamp((rep - r.rep) / (float) (next.rep - r.rep), 0.0F, 1.0F);
        Grim.progress(g, x0 + 6, y, x1 - x0 - 12, 4, f * Grim.ease(since / 14.0F), Grim.RED, a);
        y += 7;
        String toNext = next == null ? CaseText.tr("casebook.geist_hellblazer.record.top")
                : CaseText.tr("casebook.geist_hellblazer.record.to_next", Math.max(0, next.rep - rep), next.title);
        ui.say(g, Hud.fit(ui.font, toNext, Ui.S, x1 - x0 - 12), x0 + 6, y, Grim.GREY, a);
        y += lh + 6;
        y += Grim.header(g, ui.font, CaseText.tr("casebook.geist_hellblazer.record.tally"), x0 + 3, y, x1 - 4, Grim.RED, a);
        // two columns of tallies
        int colW = (x1 - x0 - 12) / 2;
        for (int i = 0; i < STATS.length; i++) {
            int cx = x0 + 6 + (i % 2) * colW, cy = y + (i / 2) * (lh * 2 + 3);
            if (cy + lh * 2 > y1 - 4) break;
            float k = Grim.ease((since - i * 1.5F) / 6.0F);
            ui.say(g, Hud.fit(ui.font, CaseText.tr("casebook.geist_hellblazer.record.stat." + STATS[i]), Ui.S, colW - 4), cx, cy, Grim.DIM, a * k);
            int v = Math.round(CaseData.stat(STATS[i]) * k);
            int c = STATS[i].equals("failed") || STATS[i].equals("abandoned") ? Grim.RED : STATS[i].equals("closed") ? Grim.GREEN : Grim.WHITE;
            Hud.say(g, ui.font, String.valueOf(v), cx, cy + lh, Ui.M, Grim.a(a * k, c));
        }
        y += ((STATS.length + 1) / 2) * (lh * 2 + 3) + 2;
        int taken = CaseData.stat("accepted"), closed = CaseData.stat("closed");
        if (taken > 0 && y + lh < y1 - 3) {
            int pct = Math.round(100.0F * closed / Math.max(1, taken));
            ui.say(g, CaseText.tr("casebook.geist_hellblazer.record.rate", Math.min(100, pct)), x0 + 6, y, Grim.GREY, a);
        }
    }

    /** The ladder of ranks, and the field notes under it (scrolls). */
    private void ladder(GuiGraphics g, Ui ui, int x0, int y0, int x1, int y1, float a) {
        Grim.panel(g, x0, y0, x1, y1, Grim.RED, a);
        int lh = ui.lh();
        ladX0 = x0 + 1;
        ladY0 = y0 + 1;
        ladX1 = x1 - 1;
        ladY1 = y1 - 1;
        g.enableScissor(ladX0, ladY0, ladX1, ladY1);
        int y = y0 + 4 - Math.round(scroll);
        int top = y;
        y += Grim.header(g, ui.font, CaseText.tr("casebook.geist_hellblazer.record.ladder"), x0 + 3, y, x1 - 4, Grim.RED, a);
        HbRank mine = CaseData.rank();
        float since = ui.t - shownAt;
        HbRank[] all = HbRank.values();
        for (int i = all.length - 1; i >= 0; i--) {
            HbRank r = all[i];
            boolean got = r.ordinal() <= mine.ordinal(), here = r == mine, nextUp = r.ordinal() == mine.ordinal() + 1;
            float k = Grim.ease((since - (all.length - 1 - i) * 1.2F) / 6.0F) * a;
            List<String> opens = Grim.wrap(ui.font, CaseText.tr("case.geist_hellblazer.rank." + r.name().toLowerCase(Locale.ROOT) + ".opens"), Ui.S, x1 - x0 - 26);
            int h = lh + 4 + lh * Math.min(2, opens.size());
            if (here) {
                g.fill(x0 + 4, y - 1, x1 - 4, y + h, Grim.a(0.22F * k, Grim.RED));
                Grim.corners(g, x0 + 4, y - 1, x1 - 4, y + h, 3, Grim.a(k, Grim.RED));
            }
            // the rung: a mark, lit for ranks held
            int mx = x0 + 9, my = y + lh / 2;
            int mc = got ? Grim.RED : nextUp ? Grim.GOLD : Grim.DIM;
            g.fill(mx - 2, my - 2, mx + 3, my + 3, Grim.a(k * (got ? 1.0F : 0.5F), mc));
            if (i > 0) g.fill(mx, my + 3, mx + 1, y + h + 2, Grim.a(0.3F * k, got ? Grim.RED : Grim.DIM));
            ui.say(g, r.title, x0 + 17, y, here ? Grim.WHITE : got ? Grim.BONE : nextUp ? Grim.GOLD : Grim.GREY, k);
            String req = r.rep == 0 ? "-" : String.format(Locale.ROOT, "%,d", r.rep);
            ui.sayRight(g, req, x1 - 8, y, got ? Grim.DIM : Grim.GREY, k);
            for (int j = 0; j < Math.min(2, opens.size()); j++) ui.say(g, opens.get(j), x0 + 17, y + lh + 1 + j * lh, got ? Grim.GREY : Grim.DIM, k);
            y += h + 3;
        }
        y += 4;
        y += Grim.header(g, ui.font, CaseText.tr("casebook.geist_hellblazer.record.notes"), x0 + 3, y, x1 - 4, Grim.GOLD, a);
        int s = Math.max(1, lh / 5);
        for (CaseType t : CaseType.values()) {
            int col = Grim.typeColor(t);
            Grim.glyph(g, Grim.typeIcon(t), x0 + 6, y + 1, s, Grim.a(a, col));
            int tx = x0 + 10 + 7 * s;
            ui.say(g, CaseText.typeName(t), tx, y, col, a);
            y += lh;
            for (String line : Grim.wrap(ui.font, CaseText.tr("casebook.geist_hellblazer.how." + t.key()), Ui.S, x1 - tx - 6)) {
                ui.say(g, line, tx, y, Grim.GREY, a);
                y += lh;
            }
            y += 3;
        }
        ladH = y + Math.round(scroll) - top + 6;
        g.disableScissor();
        if (ladH > ladY1 - ladY0) {
            float f = scroll / Math.max(1.0F, ladH - (ladY1 - ladY0));
            int th = Math.max(10, (ladY1 - ladY0) * (ladY1 - ladY0) / ladH);
            int ty = ladY0 + Math.round(f * (ladY1 - ladY0 - th));
            g.fill(x1 - 3, ty, x1 - 1, ty + th, Grim.a(0.6F * a, Grim.RED));
        }
    }
}
