package com.grapefruit.hellblazer.client.screen;

import com.grapefruit.hellblazer.client.ui.Ui;
import com.grapefruit.hellblazer.network.EventPacket;
import net.minecraft.client.gui.GuiGraphics;

/** A page of the casebook. */
interface Page {
    default void shown(float t) {}

    default void tick() {}

    default boolean scroll(double x, double y, double delta) {
        return false;
    }

    default void onEvent(EventPacket m, CasebookScreen screen) {}

    void render(GuiGraphics g, Ui ui, CasebookScreen screen, int x0, int y0, int x1, int y1, float a);
}
