package com.grapefruit.hellblazer.client.screen;

import com.grapefruit.hellblazer.client.CaseData;
import com.grapefruit.hellblazer.client.CaseText;
import com.grapefruit.hellblazer.client.ui.Grim;
import com.grapefruit.hellblazer.client.ui.Hud;
import com.grapefruit.hellblazer.client.ui.Ui;
import com.grapefruit.hellblazer.network.ActionPacket;
import com.grapefruit.hellblazer.network.EventPacket;
import com.grapefruit.hellblazer.network.HbNetwork;
import com.grapefruit.hellblazer.occult.CaseFile;
import com.grapefruit.hellblazer.occult.CaseType;
import com.grapefruit.hellblazer.occult.Cases;
import com.grapefruit.hellblazer.registry.HbSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CASES: on the left, the board (the files on offer, new leads for a few levels) and the files being worked (how far
 * along, how long left, which one the HUD follows); on the right, the file itself - a photograph of what it's about
 * (the creature, turning to follow the mouse, or the thing itself), the facts, the section's notes bleeding in, and
 * what you can do about it: take it, follow it, send the relics in, or walk away from it.
 */
final class CasesPage implements Page {
    private int selected = -1;
    private float selectedAt, shownAt, scroll, scrollTo;
    private int listX0, listY0, listX1, listY1, listH;
    private int confirmDrop = -1;
    private float confirmAt;
    private float photoX = -1.0F, photoY = -1.0F;
    private final Map<String, LivingEntity> subjects = new HashMap<>();

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
        if (x < listX0 || x >= listX1 || y < listY0 || y >= listY1) return false;
        scrollTo = Mth.clamp(scrollTo - (float) delta * 16.0F, 0.0F, Math.max(0.0F, listH - (listY1 - listY0)));
        return true;
    }

    @Override
    public void onEvent(EventPacket m, CasebookScreen screen) {
        if (m.kind == EventPacket.ACCEPTED) {
            selected = m.a;
            selectedAt = screen.time();
            screen.stamp(CaseText.tr("casebook.geist_hellblazer.stamp.taken"), Grim.RED, photoX, photoY);
        } else if (m.kind == EventPacket.CLOSED) {
            screen.stamp(CaseText.tr("casebook.geist_hellblazer.stamp.closed"), Grim.GREEN, -1.0F, -1.0F);
            selected = -1;
        }
    }

    private CaseFile current() {
        CaseFile c = selected >= 0 ? CaseData.get(selected) : null;
        if (c != null) return c;
        List<CaseFile> act = CaseData.active();
        if (!act.isEmpty()) return act.get(0);
        List<CaseFile> off = CaseData.offers();
        return off.isEmpty() ? null : off.get(0);
    }

    private void select(CaseFile c, Ui ui) {
        if (c.id != selected) {
            selected = c.id;
            selectedAt = ui.t;
            confirmDrop = -1;
        }
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, Ui ui, CasebookScreen screen, int x0, int y0, int x1, int y1, float a) {
        int split = x0 + Math.round((x1 - x0) * 0.42F);
        list(g, ui, x0, y0, split - 3, y1, a);
        CaseFile c = current();
        if (c != null && c.id != selected) {
            selected = c.id;
            selectedAt = ui.t;
        }
        file(g, ui, c, split + 3, y0, x1, y1, a);
    }

    private void list(GuiGraphics g, Ui ui, int x0, int y0, int x1, int y1, float a) {
        int lh = ui.lh();
        Grim.panel(g, x0, y0, x1, y1, Grim.RED, a);
        listX0 = x0 + 1;
        listY0 = y0 + 1;
        listX1 = x1 - 1;
        listY1 = y1 - 1;
        g.enableScissor(listX0, listY0, listX1, listY1);
        int y = y0 + 4 - Math.round(scroll);
        int top = y;
        // the board
        y += Grim.header(g, ui.font, CaseText.tr("casebook.geist_hellblazer.cases.offers"), x0 + 3, y, x1 - 4, Grim.GOLD, a);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            long left = Cases.REFRESH - (mc.level.getGameTime() - CaseData.offersAt());
            ui.sayRight(g, CaseText.tr("casebook.geist_hellblazer.cases.refresh", CaseText.clock(Math.max(20L, left))), x1 - 5, y, Grim.DIM, a);
        }
        String reroll = CaseText.tr("casebook.geist_hellblazer.cases.reroll", Cases.REROLL_LEVELS);
        int bw = Math.min(x1 - x0 - 10, ui.width(reroll) + 12);
        boolean canReroll = mc.player != null && (mc.player.experienceLevel >= Cases.REROLL_LEVELS || mc.player.getAbilities().instabuild);
        ui.button(g, x0 + 5, y, x0 + 5 + bw, y + lh + 4, reroll, "reroll", canReroll, Grim.GOLD, a,
                () -> HbNetwork.CHANNEL.sendToServer(new ActionPacket(ActionPacket.REROLL, 0)));
        y += lh + 8;
        if (!CaseData.received()) {
            ui.say(g, CaseText.tr("casebook.geist_hellblazer.cases.loading"), x0 + 7, y + 1, Grim.DIM, a);
            y += lh + 2;
        }
        int i = 0;
        for (CaseFile c : CaseData.offers()) y = offerCard(g, ui, c, x0 + 4, y, x1 - 4, a, i++) + 3;
        y += 4;
        List<CaseFile> act = CaseData.active();
        y += Grim.header(g, ui.font, CaseText.tr("casebook.geist_hellblazer.cases.active", act.size(), CaseData.maxActive()), x0 + 3, y, x1 - 4, Grim.RED, a);
        if (act.isEmpty()) {
            for (String s : Grim.wrap(ui.font, CaseText.tr("casebook.geist_hellblazer.cases.none_active"), Ui.S, x1 - x0 - 14)) {
                ui.say(g, s, x0 + 7, y + 1, Grim.DIM, a);
                y += lh;
            }
            y += 2;
        }
        for (CaseFile c : act) y = activeCard(g, ui, c, x0 + 4, y, x1 - 4, a) + 3;
        listH = y + Math.round(scroll) - top + 6;
        g.disableScissor();
        if (listH > listY1 - listY0) {
            float f = scroll / Math.max(1.0F, listH - (listY1 - listY0));
            int th = Math.max(10, (listY1 - listY0) * (listY1 - listY0) / listH);
            int ty = listY0 + Math.round(f * (listY1 - listY0 - th));
            g.fill(x1 - 3, ty, x1 - 1, ty + th, Grim.a(0.6F * a, Grim.RED));
        }
    }

    private int offerCard(GuiGraphics g, Ui ui, CaseFile c, int x0, int y, int x1, float a, int index) {
        int lh = ui.lh();
        int h = lh * 2 + 8;
        String id = "offer" + c.id;
        if (y + h > listY0 && y < listY1) ui.area(x0, Math.max(y, listY0), x1, Math.min(y + h, listY1), id, () -> {
            select(c, ui);
            ui.play(HbSounds.CASE_CLICK.get(), 1.15F, 0.4F);
        });
        float lit = ui.lit(id);
        boolean sel = c.id == selected;
        float in = Grim.ease((ui.t - shownAt - index * 2.0F) / 6.0F) * a;
        int col = Grim.typeColor(c.type);
        g.fill(x0, y, x1, y + h, Grim.a(in * (sel ? 0.26F : 0.08F + 0.12F * lit), sel ? col : Grim.RED_D));
        g.fill(x0, y, x0 + 2, y + h, Grim.a(in, col));
        if (sel) Grim.corners(g, x0, y, x1, y + h, 3, Grim.a(in, col));
        int s = Math.max(1, lh / 5);
        Grim.glyph(g, Grim.typeIcon(c.type), x0 + 5, y + (h - 7 * s) / 2, s, Grim.a(in, col));
        int tx = x0 + 9 + 7 * s;
        float reveal = (ui.t - shownAt - index * 2.0F) * 2.2F;
        if (reveal < c.name.length()) ui.typing = true;
        String pay = "+" + c.rep;
        ui.say(g, Grim.bleed(Hud.fit(ui.font, c.name, Ui.S, x1 - tx - 8 - ui.width(pay)), reveal, c.id), tx, y + 3, sel ? Grim.WHITE : Grim.BONE, in);
        ui.sayRight(g, pay, x1 - 4, y + 3, Grim.GOLD, in);
        String sub = CaseText.typeName(c.type);
        ui.say(g, sub, tx, y + 4 + lh, col, in * 0.9F);
        int ps = Math.max(2, lh / 3);
        Grim.threat(g, tx + ui.width(sub) + 5, y + 4 + lh + (lh - ps) / 2 - 1, c.threat, ps, in);
        String limit = c.ticks > 0 ? CaseText.clock(c.ticks) : CaseText.tr("casebook.geist_hellblazer.cases.no_limit_short");
        ui.sayRight(g, limit, x1 - 4, y + 4 + lh, Grim.DIM, in);
        return y + h;
    }

    private int activeCard(GuiGraphics g, Ui ui, CaseFile c, int x0, int y, int x1, float a) {
        int lh = ui.lh();
        int h = lh * 3 + 9;
        String id = "active" + c.id;
        if (y + h > listY0 && y < listY1) ui.area(x0, Math.max(y, listY0), x1, Math.min(y + h, listY1), id, () -> select(c, ui));
        float lit = ui.lit(id);
        boolean sel = c.id == selected;
        boolean tracked = c.id == CaseData.trackedId();
        int col = Grim.typeColor(c.type);
        g.fill(x0, y, x1, y + h, Grim.a(a * (sel ? 0.28F : 0.1F + 0.12F * lit), sel ? col : Grim.RED_D));
        g.fill(x0, y, x0 + 2, y + h, Grim.a(a, col));
        if (sel) Grim.corners(g, x0, y, x1, y + h, 3, Grim.a(a, col));
        String tag = tracked ? CaseText.tr("casebook.geist_hellblazer.cases.tracked") : "";
        ui.say(g, Hud.fit(ui.font, c.name, Ui.S, x1 - x0 - 14 - ui.width(tag)), x0 + 6, y + 3, Grim.WHITE, a);
        if (tracked) ui.sayRight(g, tag, x1 - 4, y + 3, Grim.GOLD, a);
        ui.say(g, Hud.fit(ui.font, CaseText.objective(c), Ui.S, x1 - x0 - 10), x0 + 6, y + 4 + lh, Grim.GREY, a);
        int by = y + 6 + lh * 2;
        String count = c.progress + "/" + c.count;
        long left = CaseText.timeLeft(c);
        String clock = CaseText.clock(left);
        int clockCol = left > 0 && left < 600 ? (((int) (ui.t / 5.0F)) % 2 == 0 ? Grim.RED : Grim.EMBER) : left > 0 && left < 1200 ? Grim.EMBER : Grim.GREY;
        int cw = ui.width(clock) + 6, nw = ui.width(count) + 6;
        Grim.progress(g, x0 + 6, by + lh / 2 - 1, Math.max(10, x1 - x0 - 16 - cw - nw), 3, c.progress / (float) Math.max(1, c.count), col, a);
        ui.sayRight(g, count, x1 - 4 - cw, by, Grim.WHITE, a);
        ui.sayRight(g, clock, x1 - 4, by, clockCol, a);
        return y + h;
    }

    // ================================================================ the file

    private LivingEntity subject(String type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        LivingEntity e = subjects.get(type);
        if (e != null && e.level() == mc.level) return e;
        Entity made = EntityType.byString(type).map(t -> t.create(mc.level)).orElse(null);
        if (made instanceof LivingEntity le) {
            subjects.put(type, le);
            return le;
        }
        return null;
    }

    /** How many of a relic the specialist is carrying. */
    private static int carried(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || item == Items.AIR) return 0;
        int n = 0;
        for (ItemStack s : mc.player.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private void file(GuiGraphics g, Ui ui, CaseFile c, int x0, int y0, int x1, int y1, float a) {
        Grim.panel(g, x0, y0, x1, y1, c == null ? Grim.RED : Grim.typeColor(c.type), a);
        int lh = ui.lh();
        if (c == null) {
            ui.sayCentered(g, CaseText.tr("casebook.geist_hellblazer.cases.empty"), (x0 + x1) / 2.0F, (y0 + y1) / 2.0F - lh / 2.0F, Grim.DIM, a);
            return;
        }
        int col = Grim.typeColor(c.type);
        float since = ui.t - selectedAt;
        // the strip across the top: a blood-red band with the kind of file
        int by1 = y0 + lh + 6;
        g.fill(x0 + 1, y0 + 1, x1 - 1, by1, Grim.a(0.22F * a, Grim.BLOOD));
        for (int hx = x0 + 1 - (int) ((ui.t * 0.4F) % 8); hx < x1; hx += 8) {
            for (int k = 0; k < 4; k++) {
                int px = hx + k;
                if (px >= x0 + 1 && px < x1 - 1) g.fill(px, y0 + 1, px + 1, y0 + 3, Grim.a(0.5F * a, col));
            }
        }
        String banner = CaseText.tr("casebook.geist_hellblazer.cases.file") + "  //  " + CaseText.typeName(c.type) + "  //  "
                + CaseText.tr(c.active ? "casebook.geist_hellblazer.cases.open" : "casebook.geist_hellblazer.cases.on_offer");
        ui.say(g, Hud.fit(ui.font, banner, Ui.S, x1 - x0 - 10), x0 + 5, y0 + 4, Grim.WHITE, a);
        int y = by1 + 4;
        float reveal = since * 2.5F;
        if (reveal < c.name.length()) ui.typing = true;
        Grim.fringe(g, ui.font, Grim.bleed(Hud.fit(ui.font, c.name, Ui.M, x1 - x0 - 10), reveal, c.id * 7), x0 + 5, y, Ui.M, Grim.BONE, a);
        y += Hud.lineH(Ui.M) + 3;
        // the photograph
        int boxW = Mth.clamp((x1 - x0) * 32 / 100, 48, 84), boxH = Math.round(boxW * 1.08F);
        int px0 = x0 + 5, py0 = y, px1 = px0 + boxW, py1 = py0 + boxH;
        g.fill(px0, py0, px1, py1, Grim.a(0.75F * a, 0x080102));
        g.fillGradient(px0, py0, px1, py1, Grim.a(0.0F, Grim.BLOOD), Grim.a(0.35F * a, Grim.BLOOD));
        photoX = (px0 + px1) / 2.0F;
        photoY = (py0 + py1) / 2.0F;
        if (a > 0.3F) {
            g.enableScissor(px0 + 1, py0 + 1, px1 - 1, py1 - 1);
            if (c.type == CaseType.HUNT || c.type == CaseType.TARGET) {
                LivingEntity e = subject(c.target);
                if (e != null) Grim.entity(g, e, (px0 + px1) / 2, py1 - 6, boxH - 12, ui.mx, ui.my);
            } else {
                ItemStack pic = CaseText.picture(c);
                Grim.item3d(g, pic, (px0 + px1) / 2.0F, (py0 + py1) / 2.0F, boxW * 0.6F, ui.t * 2.0F, 18.0F);
            }
            g.disableScissor();
        }
        for (int sy = py0 + 1; sy < py1; sy += 2) g.fill(px0, sy, px1, sy + 1, Grim.a(0.14F * a, 0x000000));
        Grim.corners(g, px0, py0, px1, py1, 6, Grim.a(a, col));
        ui.say(g, CaseText.tr("casebook.geist_hellblazer.cases.subject"), px0 + 3, py0 + 2, col, 0.8F * a);
        if (c.type == CaseType.TARGET) {
            // a red ring round the face in the photograph: this one
            float cxp = (px0 + px1) / 2.0F, cyp = py0 + boxH * 0.34F;
            Hud.arc(g, cxp, cyp, boxW * 0.2F, 0.0F, 330.0F, Grim.a(0.85F * a, Grim.RED));
            Hud.arc(g, cxp, cyp, boxW * 0.2F + 1.0F, 20.0F, 300.0F, Grim.a(0.5F * a, Grim.RED));
            if (!c.subject.isEmpty()) ui.sayCentered(g, Hud.fit(ui.font, c.subject, Ui.S, boxW + 36), (px0 + px1) / 2.0F, py1 + 2, Grim.GOLD, a);
        }
        // the facts
        int fx = px1 + 7, fw = x1 - 5 - fx;
        int fy = py0;
        fy = fact(g, ui, CaseText.tr("casebook.geist_hellblazer.cases.objective"), CaseText.objective(c), fx, fy, fw, Grim.WHITE, a);
        ui.say(g, CaseText.tr("casebook.geist_hellblazer.cases.threat"), fx, fy, Grim.DIM, a);
        int ps = Math.max(2, lh / 3);
        Grim.threat(g, fx + ui.width(CaseText.tr("casebook.geist_hellblazer.cases.threat")) + 5, fy + (lh - ps) / 2 - 1, c.threat, ps, a);
        fy += lh + 2;
        long left = CaseText.timeLeft(c);
        fy = fact(g, ui, CaseText.tr(c.active ? "casebook.geist_hellblazer.cases.time_left" : "casebook.geist_hellblazer.cases.time_limit"),
                left > 0 ? CaseText.clock(left) : CaseText.tr("casebook.geist_hellblazer.cases.no_limit"), fx, fy, fw,
                left > 0 && left < 1200 && c.active ? Grim.RED : Grim.WHITE, a);
        fy = fact(g, ui, CaseText.tr("casebook.geist_hellblazer.cases.pays"), CaseText.tr("casebook.geist_hellblazer.cases.pays_v", c.rep, c.xp, c.emeralds), fx, fy,
                fw, Grim.GREEN, a);
        Minecraft mc = Minecraft.getInstance();
        if (c.active && (c.type == CaseType.TARGET || c.type == CaseType.HAUNTING) && mc.player != null) {
            double dx = c.x + 0.5D - mc.player.getX(), dz = c.z + 0.5D - mc.player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            String label = CaseText.tr(c.type == CaseType.TARGET ? "casebook.geist_hellblazer.cases.last_seen" : "casebook.geist_hellblazer.cases.the_place");
            fy = fact(g, ui, label, c.x + ", " + c.z + "  (" + dist + " m " + CaseText.compass(dx, dz) + ")", fx, fy, fw, Grim.GOLD, a);
        }
        if (c.type == CaseType.RELIC) {
            int have = carried(CaseText.item(c));
            fy = fact(g, ui, CaseText.tr("casebook.geist_hellblazer.cases.carrying"), have + " / " + c.count, fx, fy, fw, have >= c.count ? Grim.GREEN : Grim.GREY, a);
        }
        if (c.active) {
            Grim.progress(g, fx, fy + 2, fw, 4, c.progress / (float) Math.max(1, c.count), col, a);
            fy += 9;
        }
        // the section's notes
        int bry = Math.max(py1 + lh + 6, fy + 4);
        int footer = lh + 10;
        bry += Grim.header(g, ui.font, CaseText.tr("casebook.geist_hellblazer.cases.notes"), x0 + 3, bry, x1 - 4, Grim.RED, a);
        Minecraft mc2 = Minecraft.getInstance();
        String name = mc2.player != null ? mc2.player.getGameProfile().getName() : "";
        List<String> lines = Grim.wrap(ui.font, CaseText.briefing(c, name), Ui.S, x1 - x0 - 12);
        float typed = Math.max(0.0F, since - 4.0F) * 3.2F;
        int maxLines = Math.max(1, (y1 - footer - bry) / lh);
        int done = 0;
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            String s = lines.get(i);
            float r = typed - done;
            if (r <= 0.0F) break;
            if (r < s.length()) ui.typing = true;
            ui.say(g, Grim.bleed(s, r, i * 13 + c.id), x0 + 6, bry + i * lh, Grim.BONE, a * 0.92F);
            done += s.length();
        }
        actions(g, ui, c, x0 + 5, y1 - lh - 8, x1 - 5, y1 - 4, a);
    }

    private int fact(GuiGraphics g, Ui ui, String label, String value, int x, int y, int w, int valueColor, float a) {
        int lh = ui.lh();
        ui.say(g, label, x, y, Grim.DIM, a);
        List<String> v = Grim.wrap(ui.font, value, Ui.S, w);
        for (int i = 0; i < Math.min(2, v.size()); i++) ui.say(g, v.get(i), x, y + lh * (i + 1) - 1, valueColor, a);
        return y + lh * (1 + Math.min(2, v.size())) + 1;
    }

    private void actions(GuiGraphics g, Ui ui, CaseFile c, int x0, int y0, int x1, int y1, float a) {
        int gap = 4;
        if (!c.active) {
            boolean room = CaseData.active().size() < CaseData.maxActive();
            int w = Math.min(x1 - x0, Math.max(80, (x1 - x0) / 2));
            ui.button(g, x1 - w, y0, x1, y1, CaseText.tr(room ? "casebook.geist_hellblazer.cases.accept" : "casebook.geist_hellblazer.cases.full"), "accept" + c.id,
                    room, Grim.RED, a, () -> HbNetwork.CHANNEL.sendToServer(new ActionPacket(ActionPacket.ACCEPT, c.id)));
            return;
        }
        int n = c.type == CaseType.RELIC ? 3 : 2;
        int w = (x1 - x0 - gap * (n - 1)) / n;
        int bx = x0;
        boolean tracked = c.id == CaseData.trackedId();
        ui.button(g, bx, y0, bx + w, y1, CaseText.tr(tracked ? "casebook.geist_hellblazer.cases.following" : "casebook.geist_hellblazer.cases.follow"),
                "track" + c.id, !tracked, Grim.GOLD, a, () -> CaseData.track(c.id));
        bx += w + gap;
        if (c.type == CaseType.RELIC) {
            boolean enough = carried(CaseText.item(c)) >= c.count;
            ui.button(g, bx, y0, bx + w, y1, CaseText.tr("casebook.geist_hellblazer.cases.deliver"), "deliver" + c.id, true, enough ? Grim.GREEN : Grim.GREY, a,
                    () -> HbNetwork.CHANNEL.sendToServer(new ActionPacket(ActionPacket.DELIVER, c.id)));
            bx += w + gap;
        }
        boolean confirming = confirmDrop == c.id && ui.t - confirmAt < 60.0F;
        ui.button(g, bx, y0, bx + w, y1, CaseText.tr(confirming ? "casebook.geist_hellblazer.cases.confirm_drop" : "casebook.geist_hellblazer.cases.drop"),
                "drop" + c.id, true, confirming ? Grim.RED : Grim.GREY, a, () -> {
                    if (confirmDrop == c.id && ui.t - confirmAt < 60.0F) {
                        HbNetwork.CHANNEL.sendToServer(new ActionPacket(ActionPacket.ABANDON, c.id));
                        confirmDrop = -1;
                        selected = -1;
                    } else {
                        confirmDrop = c.id;
                        confirmAt = ui.t;
                    }
                });
    }
}
