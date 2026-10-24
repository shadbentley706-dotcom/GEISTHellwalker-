package com.grapefruit.hellblazer.client.screen;

import com.grapefruit.hellblazer.client.CaseData;
import com.grapefruit.hellblazer.client.CaseHud;
import com.grapefruit.hellblazer.client.CaseText;
import com.grapefruit.hellblazer.client.ui.Grim;
import com.grapefruit.hellblazer.client.ui.Hud;
import com.grapefruit.hellblazer.client.ui.Ui;
import com.grapefruit.hellblazer.network.EventPacket;
import com.grapefruit.hellblazer.occult.HbRank;
import com.grapefruit.hellblazer.registry.HbSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * The casebook (key K): a screen of dark red glass with a crack through it. It comes on like an old set - a bright
 * line opens into a picture, something strikes the glass and the crack runs out from where it hit, the sigil burns up
 * out of the dark and the heading bleeds in - and then lays out two pages: CASES (the board, the files being worked,
 * the file itself) and RECORD (standing, the ladder of ranks, what each kind of file asks). The crack is the same one
 * every time you open it: it's your casebook. Pages slide in when you switch (click a tab, 1-2, or TAB); stamps come
 * down on what you take; closing squeezes the picture to a line and puts it out.
 */
public class CasebookScreen extends Screen {
    private static final String[] TABS = {"cases", "record"};
    private static int lastPage;
    private static long lastClosed = -100000L;

    private final Ui ui;
    private final Page[] pages = {new CasesPage(), new RecordPage()};
    private final long crackSeed;
    private int page = lastPage, age, closing = -1;
    private float pageAt, openLen = 26.0F, lastT, typeClock;
    private String stampText = "";
    private int stampColor = Grim.RED;
    private float stampAt = -999.0F, stampX = -1.0F, stampY = -1.0F;
    private String flash = "";
    private int flashColor = Grim.RED;
    private float flashAt = -999.0F;
    private boolean struck;
    /** The content area this frame (for anchoring stamps). */
    private int cx0, cy0, cx1, cy1;

    public CasebookScreen() {
        super(Component.translatable("casebook.geist_hellblazer.title"));
        this.ui = new Ui(Minecraft.getInstance().font);
        Minecraft mc = Minecraft.getInstance();
        this.crackSeed = mc.player != null ? mc.player.getUUID().getLeastSignificantBits() ^ 0x5EEDL : 0x5EEDL;
    }

    @Override
    protected void init() {
        if (age == 0) {
            Minecraft mc = Minecraft.getInstance();
            long now = mc.level != null ? mc.level.getGameTime() : 0L;
            boolean quick = now - lastClosed < 600L;
            openLen = quick ? 6.0F : 26.0F;
            struck = quick;
            pageAt = openLen;
            ui.play(HbSounds.CASE_OPEN.get(), quick ? 1.25F : 1.0F, 0.6F);
            pages[page].shown(openLen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public float time() {
        return ui.t;
    }

    // ================================================================ ticking

    @Override
    public void tick() {
        age++;
        if (closing >= 0 && ++closing > 6) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) lastClosed = mc.level.getGameTime();
            lastPage = page;
            mc.setScreen(null);
            return;
        }
        for (Page p : pages) p.tick();
        if (ui.typing && ++typeClock >= 2.0F) {
            typeClock = 0.0F;
            ui.play(HbSounds.CASE_TYPE.get(), 0.9F + (age % 5) * 0.06F, 0.14F);
        }
    }

    @Override
    public void onClose() {
        if (closing < 0) {
            closing = 0;
            ui.play(HbSounds.CASE_CLOSE.get(), 1.0F, 0.5F);
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) lastClosed = mc.level.getGameTime();
        lastPage = page;
    }

    // ================================================================ input

    private void skipBoot() {
        if (age < openLen) {
            openLen = age;
            pageAt = age;
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (closing >= 0) return true;
        if (age < openLen) {
            skipBoot();
            return true;
        }
        if (button == 0 && ui.click(x, y)) return true;
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (pages[page].scroll(x, y, delta)) return true;
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (CaseHud.CASEBOOK.matches(key, scan)) {
            onClose();
            return true;
        }
        skipBoot();
        if (key == GLFW.GLFW_KEY_TAB) {
            goTo((page + ((mods & GLFW.GLFW_MOD_SHIFT) != 0 ? TABS.length - 1 : 1)) % TABS.length);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + TABS.length) {
            goTo(key - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    public void goTo(int p) {
        if (p == page || p < 0 || p >= pages.length) return;
        page = p;
        pageAt = age;
        pages[p].shown(age);
        ui.play(HbSounds.CASE_TAB.get(), 1.0F + p * 0.06F, 0.5F);
    }

    // ================================================================ what the pages ask for

    /** A stamp comes down at (x, y) (or the middle of the page if x < 0). */
    public void stamp(String text, int color, float x, float y) {
        stampText = text;
        stampColor = color;
        stampAt = ui.t;
        stampX = x;
        stampY = y;
        ui.play(HbSounds.CASE_STAMP.get(), 0.95F + 0.1F * (float) Math.random(), 0.85F);
    }

    /** A message across the top of the page for a few seconds. */
    public void flash(String text, int color) {
        flash = text;
        flashColor = color;
        flashAt = ui.t;
    }

    /** From the server, while the casebook is open. */
    public void onEvent(EventPacket m) {
        switch (m.kind) {
            case EventPacket.DENIED -> {
                flash(CaseText.denial(m.text, m.a, m.b), Grim.RED);
                ui.play(HbSounds.CASE_DENY.get(), 1.0F, 0.7F);
            }
            case EventPacket.CLOSED -> flash(CaseText.tr("casebook.geist_hellblazer.flash.closed", m.text, m.a, m.b), Grim.GREEN);
            case EventPacket.FAILED -> flash(CaseText.tr("casebook.geist_hellblazer.flash.failed", m.text), Grim.RED);
            default -> {
            }
        }
        for (Page p : pages) p.onEvent(m, this);
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        float t = age + pt;
        float dt = Math.max(0.0F, t - lastT);
        lastT = t;
        ui.begin(mouseX, mouseY, t);
        CaseData.rollRep(dt);
        int pw = Math.min(width - 4, Mth.clamp(width - 16, 300, 580)), ph = Math.min(height - 4, Mth.clamp(height - 16, 200, 340));
        int x0 = (width - pw) / 2, y0 = (height - ph) / 2, x1 = x0 + pw, y1 = y0 + ph;
        g.fill(0, 0, width, height, Grim.a(0.55F, 0x000000));
        g.fillGradient(0, 0, width, height / 2, Grim.a(0.0F, Grim.BLOOD), Grim.a(0.12F, Grim.BLOOD));
        g.fillGradient(0, height / 2, width, height, Grim.a(0.12F, Grim.BLOOD), Grim.a(0.0F, Grim.BLOOD));

        if (closing >= 0) {
            // the picture squeezes to a bright line and the line goes out
            float k = Mth.clamp((closing + pt) / 6.0F, 0.0F, 1.0F);
            int mid = (y0 + y1) / 2;
            int hh = Math.max(1, Math.round(ph / 2.0F * (1.0F - Grim.easeInOut(Math.min(1.0F, k * 1.6F)))));
            int ww = Math.round(pw / 2.0F * (1.0F - Grim.easeInOut(Math.max(0.0F, k * 1.6F - 0.6F))));
            Grim.screen(g, x0, mid - hh, x1, mid + hh, t, 1.0F - k * 0.5F);
            Grim.cracks(g, x0, mid - hh, x1, mid + hh, crackSeed, 1.0F, 0.7F * (1.0F - k));
            g.fill((x0 + x1) / 2 - ww, mid - 1, (x0 + x1) / 2 + ww, mid + 1, Grim.a(0.9F, 0xFFD0C8));
            ui.end(dt);
            return;
        }

        if (t < openLen) {
            boot(g, x0, y0, x1, y1, t);
            ui.end(dt);
            return;
        }

        float in = Grim.ease((t - openLen) / 8.0F);
        Grim.screen(g, x0, y0, x1, y1, t, 1.0F);
        // the sigil, faint, behind everything
        float sh = ph * 0.82F;
        Grim.sigil(g, x1 - sh * 0.5F - pw * 0.08F, y0 + (ph - sh) / 2.0F, sh * 0.5F, sh, Grim.RED, 0.045F * in);
        Hud.frame(g, x0, y0, x1 - 1, y1 - 1, 4, Grim.RED, 0.8F, 1.0F);
        int lh = ui.lh();
        int barH = lh + 7, tabH = lh + 8, footH = lh + 5;
        topBar(g, x0, y0, x1, y0 + barH, t, in);
        int tx0 = x0 + 5, tx1 = x1 - 5, ty = y0 + barH + 3;
        tabs(g, tx0, ty, tx1, ty + tabH, t, in);
        cx0 = tx0;
        cy0 = ty + tabH + 4;
        cx1 = tx1;
        cy1 = y1 - footH - 3;
        float k = Grim.ease((t - pageAt) / 7.0F);
        int ox = Math.round((1.0F - k) * 16.0F);
        pages[page].render(g, ui, this, cx0 + ox, cy0, cx1 + ox, cy1, k * in);
        footer(g, x0, y1 - footH, x1, y1, t, in);
        // the glass over it all
        Grim.cracks(g, x0, y0, x1, y1, crackSeed, 1.0F, 0.72F);
        tear(g, x0, y0, x1, y1, t);
        overlays(g, t);
        ui.end(dt);
    }

    /** Coming on: a bright line opens into a picture, the glass is struck, the sigil burns up, the heading bleeds in. */
    private void boot(GuiGraphics g, int x0, int y0, int x1, int y1, float t) {
        float L = Math.max(1.0F, openLen);
        float s = 26.0F / L;                                                    // a quick reopen runs the same steps faster
        float tt = t * s;
        int mid = (y0 + y1) / 2;
        float open = Grim.ease(tt / 5.0F);
        int hh = Math.max(1, Math.round((y1 - y0) / 2.0F * open));
        Grim.screen(g, x0, mid - hh, x1, mid + hh, t, 0.4F + 0.6F * open);
        if (open < 1.0F) {
            int ww = Math.round((x1 - x0) / 2.0F * Mth.clamp(tt / 2.0F, 0.0F, 1.0F));
            g.fill((x0 + x1) / 2 - ww, mid - 1, (x0 + x1) / 2 + ww, mid + 1, Grim.a(0.9F * (1.0F - open), 0xFFD0C8));
            return;
        }
        Hud.frame(g, x0, y0, x1 - 1, y1 - 1, 4, Grim.RED, 0.8F, Grim.ease((tt - 4.0F) / 6.0F));
        // struck: a white flash, then the crack runs out
        if (!struck && tt >= 5.0F) {
            struck = true;
            ui.play(HbSounds.CASE_STAMP.get(), 0.6F, 0.55F);
        }
        float hit = tt - 5.0F;
        if (hit >= 0.0F && hit < 2.5F) g.fill(x0, y0, x1, y1, Grim.a(0.18F * (1.0F - hit / 2.5F), 0xFFE8E0));
        float cx = (x0 + x1) / 2.0F, cy = (y0 + y1) / 2.0F - (y1 - y0) * 0.08F;
        // the sigil burns up out of the dark
        float sk = Mth.clamp((tt - 8.0F) / 9.0F, 0.0F, 1.0F);
        if (sk > 0.0F) {
            float sh = (y1 - y0) * 0.5F, sw = sh * 0.5F;
            float flick = sk < 1.0F ? (Grim.hash((int) (t * 3.0F), 41) < 0.25F ? 0.35F : 1.0F) : 0.85F + 0.15F * Mth.sin(t * 0.7F);
            Grim.sigil(g, cx - sw / 2.0F - 1.5F, cy - sh / 2.0F, sw, sh, 0xFF1020, 0.3F * sk * flick);
            Grim.sigil(g, cx - sw / 2.0F, cy - sh / 2.0F, sw, sh, Grim.RED, sk * flick);
            int ty = Math.round(cy + sh / 2.0F + 6);
            float hk = (tt - 13.0F) * 2.4F;
            if (hk > 0.0F) {
                String head = CaseText.tr("casebook.geist_hellblazer.boot.head");
                if (hk < head.length()) ui.typing = true;
                Grim.fringe(g, font, Grim.bleed(head, hk, 3), cx - Hud.textW(font, head, Hud.MEDIUM) / 2.0F, ty, Hud.MEDIUM, Grim.BONE, 1.0F);
            }
            float nk = (tt - 17.0F) * 3.0F;
            if (nk > 0.0F) {
                Minecraft mc = Minecraft.getInstance();
                String who = (mc.player != null ? mc.player.getGameProfile().getName() : "?") + "  //  " + CaseData.rank().title;
                Hud.sayCentered(g, font, Grim.bleed(who, nk, 9), cx, ty + Hud.lineH(Hud.MEDIUM) + 3, Hud.SMALL, Grim.a(1.0F, Grim.RED));
            }
        }
        Grim.cracks(g, x0, y0, x1, y1, crackSeed, Grim.ease(Math.max(0.0F, hit) / 7.0F), 0.8F);
    }

    /** Now and then the picture tears for a moment, like a set that took a knock. */
    private static void tear(GuiGraphics g, int x0, int y0, int x1, int y1, float t) {
        float cyc = t % 230.0F;
        if (cyc > 5.0F) return;
        RandomSource r = RandomSource.create((long) (t / 2.0F));
        for (int i = 0; i < 5; i++) {
            int y = y0 + r.nextInt(Math.max(1, y1 - y0)), h = 1 + r.nextInt(2);
            int xa = x0 + r.nextInt(Math.max(1, (x1 - x0) / 2)), xb = Math.min(x1, xa + (x1 - x0) / 4 + r.nextInt(Math.max(1, (x1 - x0) / 2)));
            g.fill(xa, y, xb, y + h, Grim.a(0.15F + 0.3F * r.nextFloat(), r.nextFloat() < 0.7F ? Grim.RED : Grim.BONE));
        }
    }

    private void topBar(GuiGraphics g, int x0, int y0, int x1, int y1, float t, float a) {
        int lh = ui.lh();
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1, Grim.a(0.5F * a, Grim.PANEL));
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, Grim.a(0.45F * a, Grim.RED));
        int sh = y1 - y0 - 3;
        Grim.sigil(g, x0 + 6, y0 + 2, sh * 0.5F, sh, Grim.RED, a);
        int tx = x0 + 10 + Math.round(sh * 0.5F);
        int ty = y0 + (y1 - y0 - lh) / 2 + 1;
        String head = CaseText.tr("casebook.geist_hellblazer.head");
        Grim.fringe(g, font, head, tx, ty, Hud.SMALL, Grim.BONE, a);
        ui.say(g, CaseText.tr("casebook.geist_hellblazer.sub"), tx + ui.width(head) + 8, ty, Grim.DIM, a);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            long day = mc.level.getDayTime();
            int hours = (int) ((day / 1000L + 6L) % 24L), mins = (int) ((day % 1000L) * 60L / 1000L);
            boolean night = mc.level.isNight();
            String moon = CaseText.tr("casebook.geist_hellblazer.moon." + mc.level.getMoonPhase());
            String clock = String.format(Locale.ROOT, "%02d:%02d", hours, mins);
            ui.sayRight(g, clock, x1 - 7, ty, night ? Grim.RED : Grim.GREY, a);
            ui.sayRight(g, moon + "  ·", x1 - 10 - ui.width(clock), ty, night ? Grim.BONE : Grim.DIM, a);
        }
    }

    private void tabs(GuiGraphics g, int x0, int y0, int x1, int y1, float t, float a) {
        int lh = ui.lh();
        int x = x0;
        for (int i = 0; i < TABS.length; i++) {
            String label = (i + 1) + "  " + CaseText.tr("casebook.geist_hellblazer.tab." + TABS[i]);
            if (i == 0 && !CaseData.active().isEmpty()) label += "  [" + CaseData.active().size() + "]";
            int w = ui.width(label) + 18;
            String id = "tab" + i;
            final int idx = i;
            ui.area(x, y0, x + w, y1, id, () -> goTo(idx));
            float lit = ui.lit(id);
            boolean on = i == page;
            g.fill(x, y0, x + w, y1, Grim.a(a * (on ? 0.32F : 0.08F + 0.14F * lit), on ? Grim.RED : Grim.RED_D));
            if (on) {
                g.fill(x, y1 - 2, x + w, y1, Grim.a(a, Grim.RED));
                Grim.corners(g, x, y0, x + w, y1, 3, Grim.a(a, Grim.RED));
            }
            ui.say(g, label, x + 9, y0 + (y1 - y0 - lh) / 2 + 1, on ? Grim.WHITE : Grim.GREY, a);
            x += w + 3;
        }
        // standing, top right
        HbRank r = CaseData.rank();
        String rep = CaseText.tr("casebook.geist_hellblazer.standing", String.format(Locale.ROOT, "%,d", CaseData.shownRep()));
        int ry = y0 + (y1 - y0 - lh) / 2 + 1;
        ui.sayRight(g, r.title, x1 - 2, ry, Grim.RED, a);
        ui.sayRight(g, rep + "   ", x1 - 2 - ui.width(r.title), ry, Grim.BONE, a);
        g.fill(x0, y1 + 1, x1, y1 + 2, Grim.a(0.25F * a, Grim.RED));
    }

    private void footer(GuiGraphics g, int x0, int y0, int x1, int y1, float t, float a) {
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, Grim.a(0.3F * a, Grim.RED));
        int y = y0 + 3;
        String key = CaseHud.CASEBOOK.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        ui.say(g, CaseText.tr("casebook.geist_hellblazer.footer", key), x0 + 7, y, Grim.DIM, a);
        ui.sayRight(g, CaseText.tr("casebook.geist_hellblazer.footer_right"), x1 - 7, y, Grim.DIM, a);
    }

    private void overlays(GuiGraphics g, float t) {
        float fa = t - flashAt;
        if (fa >= 0.0F && fa < 70.0F && !flash.isEmpty()) {
            float al = Mth.clamp(fa / 3.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((fa - 58.0F) / 12.0F, 0.0F, 1.0F));
            int w = Math.min(cx1 - cx0 - 10, ui.width(flash) + 20), h = ui.lh() + 6;
            int mx = (cx0 + cx1) / 2, y = cy0 + 2;
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 350.0F);
            g.fill(mx - w / 2, y, mx + w / 2, y + h, Grim.a(0.92F * al, 0x0A0203));
            g.fill(mx - w / 2, y, mx + w / 2, y + 1, Grim.a(al, flashColor));
            g.fill(mx - w / 2, y + h - 1, mx + w / 2, y + h, Grim.a(al * 0.6F, flashColor));
            ui.sayCentered(g, Hud.fit(font, flash, Ui.S, w - 8), mx, y + 3, flashColor, al);
            g.pose().popPose();
        }
        float st = t - stampAt;
        if (st >= 0.0F && st < 70.0F && !stampText.isEmpty()) {
            float x = stampX >= 0.0F ? stampX : (cx0 + cx1) / 2.0F, y = stampY >= 0.0F ? stampY : (cy0 + cy1) / 2.0F;
            Grim.stamp(g, font, stampText, x, y, st, stampColor, 0.95F);
        }
    }
}
