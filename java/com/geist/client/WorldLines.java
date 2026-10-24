package com.geist.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Glowing wireframe lines drawn in the world (optionally visible through walls). Positions are world coordinates. */
public final class WorldLines {
    private static Vec3 cam = Vec3.ZERO;
    private static boolean xray;

    private WorldLines() {}

    public static BufferBuilder begin(Vec3 cameraPos, boolean throughWalls, float width) {
        cam = cameraPos;
        xray = throughWalls;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (throughWalls) RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(width);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        return b;
    }

    public static void end() {
        Tesselator.getInstance().end();
        RenderSystem.lineWidth(1.0F);
        if (xray) RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void line(PoseStack ps, BufferBuilder b, double x0, double y0, double z0, double x1, double y1, double z1, int rgb, float alpha) {
        Vector3f n = new Vector3f((float) (x1 - x0), (float) (y1 - y0), (float) (z1 - z0));
        if (n.lengthSquared() < 1.0E-8F) return;
        n.normalize();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF, a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        PoseStack.Pose p = ps.last();
        b.vertex(p.pose(), (float) (x0 - cam.x), (float) (y0 - cam.y), (float) (z0 - cam.z)).color(r, g, bl, a).normal(p.normal(), n.x(), n.y(), n.z()).endVertex();
        b.vertex(p.pose(), (float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z)).color(r, g, bl, a).normal(p.normal(), n.x(), n.y(), n.z()).endVertex();
    }

    public static void box(PoseStack ps, BufferBuilder b, AABB box, int rgb, float alpha) {
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        line(ps, b, x0, y0, z0, x1, y0, z0, rgb, alpha); line(ps, b, x0, y0, z1, x1, y0, z1, rgb, alpha);
        line(ps, b, x0, y1, z0, x1, y1, z0, rgb, alpha); line(ps, b, x0, y1, z1, x1, y1, z1, rgb, alpha);
        line(ps, b, x0, y0, z0, x0, y0, z1, rgb, alpha); line(ps, b, x1, y0, z0, x1, y0, z1, rgb, alpha);
        line(ps, b, x0, y1, z0, x0, y1, z1, rgb, alpha); line(ps, b, x1, y1, z0, x1, y1, z1, rgb, alpha);
        line(ps, b, x0, y0, z0, x0, y1, z0, rgb, alpha); line(ps, b, x1, y0, z0, x1, y1, z0, rgb, alpha);
        line(ps, b, x0, y0, z1, x0, y1, z1, rgb, alpha); line(ps, b, x1, y0, z1, x1, y1, z1, rgb, alpha);
    }
}
