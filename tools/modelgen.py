"""Shared machinery for GEIST: Hellblazer's generated entity and armour models (ModelPart rigs).

A Rig is a tree of groups (each with a pivot and a rotation, relative to its parent) holding boxes; every box gets a
patch of a texture atlas painted per pixel by its material function (base colour) and, optionally, a glow function
(the emissive mask the renderers draw again in light). Out come the Java model class, the base and glow textures, and
preview images drawn with the same maths ModelPart uses.

Tool frame (what the parts lists are written in): pixels, y up, +z the model's front ("nose"), +x the model's left
side as it faces you... in other words the frame Minecraft uses for an entity facing south. The Java model is model
space, (x, -y, -z); renderers turn it back with scale(1, -1, -1) (an even number of flips, so face winding and
normals stay right). Rotations convert as x -> x, y -> -y, z -> -z.

Faces, by the tool axis they look along: "+y" top, "-y" bottom, "+z" front, "-z" back, "+x" and "-x" the sides. On
every face, painted pixel (px, py) has py = 0 at the top edge (for "+y"/"-y": toward -z / +z, see below) and reads
the right way round seen from outside the box. Top faces read with the model's front at the bottom of the image.
"""
import math
import os
import random
import zlib

import numpy as np
from PIL import Image, ImageDraw

# the mod this generates code for
MOD_IMPORT = "com.grapefruit.hellblazer.HellblazerMod"
MOD_CLASS = "HellblazerMod"

FONT5 = {
    "A": ["010", "101", "111", "101", "101"], "B": ["110", "101", "110", "101", "110"], "C": ["011", "100", "100", "100", "011"],
    "D": ["110", "101", "101", "101", "110"], "E": ["111", "100", "110", "100", "111"], "F": ["111", "100", "110", "100", "100"],
    "G": ["011", "100", "101", "101", "011"], "H": ["101", "101", "111", "101", "101"], "I": ["111", "010", "010", "010", "111"],
    "J": ["001", "001", "001", "101", "010"], "K": ["101", "101", "110", "101", "101"], "L": ["100", "100", "100", "100", "111"],
    "M": ["101", "111", "111", "101", "101"], "N": ["110", "101", "101", "101", "101"], "O": ["010", "101", "101", "101", "010"],
    "P": ["110", "101", "110", "100", "100"], "Q": ["010", "101", "101", "110", "011"], "R": ["110", "101", "110", "101", "101"],
    "S": ["011", "100", "010", "001", "110"], "T": ["111", "010", "010", "010", "010"], "U": ["101", "101", "101", "101", "111"],
    "V": ["101", "101", "101", "101", "010"], "W": ["101", "101", "111", "111", "101"], "X": ["101", "101", "010", "101", "101"],
    "Y": ["101", "101", "010", "010", "010"], "Z": ["111", "001", "010", "100", "111"], "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"], "2": ["110", "001", "010", "100", "111"], "3": ["110", "001", "010", "001", "110"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "110", "001", "110"], "6": ["011", "100", "110", "101", "010"],
    "7": ["111", "001", "010", "010", "010"], "8": ["010", "101", "010", "101", "010"], "9": ["010", "101", "011", "001", "110"],
    "-": ["000", "000", "111", "000", "000"], ".": ["000", "000", "000", "000", "010"], "/": ["001", "001", "010", "100", "100"],
    " ": ["000", "000", "000", "000", "000"], ":": ["000", "010", "000", "010", "000"],
}


def text_width(s, scale=1):
    return (len(s) * 4 - 1) * scale if s else 0


def text_hit(s, x0, y0, px, py, scale=1):
    """Is pixel (px, py) inked by text s whose top-left is (x0, y0)?"""
    if py < y0 or py >= y0 + 5 * scale or px < x0:
        return False
    i = (px - x0) // (4 * scale)
    if i >= len(s):
        return False
    cx = (px - x0 - i * 4 * scale) // scale
    if cx >= 3:
        return False
    g = FONT5.get(s[i].upper(), FONT5[" "])
    return g[(py - y0) // scale][cx] == "1"


def clamp(v):
    return max(0, min(255, int(round(v))))


def hn(a, b, seed=0):
    n = (a * 73856093) ^ (b * 19349663) ^ (seed * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def mix(c0, c1, k):
    return tuple(c0[i] + (c1[i] - c0[i]) * k for i in range(3))


def shade(col, c, top=1.1, bottom=0.62, grad=0.12):
    """Directional shading baked in: tops a little brighter, bottoms darker, a soft gradient down the sides."""
    f = c["face"]
    if f == "+y":
        k = top
    elif f == "-y":
        k = bottom
    else:
        k = 1.0 - grad * (c["py"] / max(1, c["H"] - 1))
        if f in ("+x", "-x"):
            k *= 0.94
    return tuple(clamp(v * k) for v in col[:3])


def edge(c, n=1):
    return c["px"] < n or c["py"] < n or c["px"] >= c["W"] - n or c["py"] >= c["H"] - n


def side(c):
    return c["face"] in ("+x", "-x", "+z", "-z")


def loc(c):
    """Where texel (px, py) sits on its box, tool frame, measured from the box's min corner (pixels)."""
    x0, y0, z0, w, h, d = c["box"]
    u, v = (c["px"] + 0.5) / c["W"], (c["py"] + 0.5) / c["H"]
    f = c["face"]
    if f == "+x":
        return (w, h * (1 - v), d * (1 - u))
    if f == "-x":
        return (0.0, h * (1 - v), d * u)
    if f == "+y":
        return (w * u, h, d * v)
    if f == "-y":
        return (w * u, 0.0, d * v)
    if f == "+z":
        return (w * u, h * (1 - v), d)
    return (w * (1 - u), h * (1 - v), 0.0)


def gloc(c):
    """loc() in the box's group frame."""
    x0, y0, z0 = c["box"][:3]
    lx, ly, lz = loc(c)
    return (x0 + lx, y0 + ly, z0 + lz)


def bevel(c, col, hi=1.18, lo=0.72, n=1):
    """A one-texel bevel: the upper/left rim catches light, the lower/right rim falls into shadow."""
    px, py, W, H = c["px"], c["py"], c["W"], c["H"]
    if W <= 2 * n or H <= 2 * n:
        return col
    if py < n or px < n:
        return tuple(clamp(v * hi) for v in col[:3])
    if py >= H - n or px >= W - n:
        return tuple(clamp(v * lo) for v in col[:3])
    return col


def sid(name):
    return zlib.crc32(name.encode()) & 0xFFFF


def grain(c, col, amt=6, seed=0):
    k = (hn(c["px"] + sid(c["name"]) % 997, c["py"], seed) - 0.5) * 2 * amt
    return tuple(clamp(v + k) for v in col[:3])


def scuff(c, col, rate=0.03, seed=5, k=0.8):
    """Sparse worn specks."""
    if hn(c["px"] * 3 + 7, c["py"] * 5 + sid(c["name"]) % 113, seed) < rate:
        return tuple(clamp(v * k) for v in col[:3])
    return col


class Rig:
    def __init__(self, uv_w, uv_h, px, seed=0):
        self.UVW, self.UVH, self.PX = uv_w, uv_h, px
        self.groups = {}
        self.order = []
        self.parts = []
        self.mat = {}
        self.glow = {}
        self.layers = {}          # extra emissive layers: {suffix: {mat: fn}} -> <name>_<suffix>.png
        self.layer_imgs = {}
        self.rng = random.Random(seed)
        self.uv = {}
        self.grow = {}            # part name -> CubeDeformation (geometry inflated, UVs as built)
        self.base = self.glowimg = None

    # ------------------------------------------------------------ building
    def group(self, name, parent=None, pivot=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.0)):
        assert name not in self.groups, name
        assert parent is None or parent in self.groups, parent
        self.groups[name] = (parent, tuple(pivot), tuple(rot))
        self.order.append(name)
        return name

    def snap(self, v):
        return round(v * self.PX) / self.PX

    def part(self, group, name, box, mat, grow=0.0):
        assert group in self.groups, group
        assert all(p[1] != name for p in self.parts), name
        x, y, z, w, h, d = (self.snap(v) for v in box)
        assert w > 0 and h > 0 and d > 0, (name, box)
        self.parts.append((group, name, (x, y, z, w, h, d), mat))
        if grow:
            self.grow[name] = grow

    def pair(self, group_l, group_r, name, box, mat, grow=0.0):
        """A part and its mirror across x = 0 (in two mirror-image groups, or the same group)."""
        x, y, z, w, h, d = box
        self.part(group_l, name + "_l", box, mat, grow)
        self.part(group_r, name + "_r", (-x - w, y, z, w, h, d), mat, grow)

    # ------------------------------------------------------------ UV atlas
    @staticmethod
    def need(box):
        x, y, z, w, h, d = box
        return (math.ceil(2 * (d + w)) + 1, math.ceil(d + h) + 1)

    def pack(self):
        order = sorted(range(len(self.parts)), key=lambda i: (-self.need(self.parts[i][2])[1], -self.need(self.parts[i][2])[0]))
        cx = cy = rowh = 0
        for i in order:
            g, n, b, m = self.parts[i]
            W, H = self.need(b)
            if cx + W > self.UVW:
                cx, cy, rowh = 0, cy + rowh, 0
            self.uv[n] = (cx, cy)
            cx += W
            rowh = max(rowh, H)
        assert cy + rowh <= self.UVH, f"UV overflow: {cy + rowh} > {self.UVH}"
        self.rows_used = cy + rowh

    @staticmethod
    def faces(u, v, w, h, d):
        return {"+y": (u + d, v, w, d), "-y": (u + d + w, v, w, d), "-x": (u, v + d, d, h), "+z": (u + d, v + d, w, h),
                "+x": (u + d + w, v + d, d, h), "-z": (u + 2 * d + w, v + d, w, h)}

    def paint(self):
        if not self.uv:
            self.pack()
        P = self.PX
        self.base = Image.new("RGBA", (self.UVW * P, self.UVH * P), (0, 0, 0, 0))
        self.glowimg = Image.new("RGBA", (self.UVW * P, self.UVH * P), (0, 0, 0, 0))
        bp, gp = self.base.load(), self.glowimg.load()
        self.layer_imgs = {k: Image.new("RGBA", (self.UVW * P, self.UVH * P), (0, 0, 0, 0)) for k in self.layers}
        lp = {k: im.load() for k, im in self.layer_imgs.items()}
        for (g, n, b, m) in self.parts:
            x0, y0, z0, w, h, d = b
            u, v = self.uv[n]
            for face, (fu, fv, fw, fh) in self.faces(u, v, w, h, d).items():
                W, H = int(round(fw * P)), int(round(fh * P))
                ox, oy = int(round(fu * P)), int(round(fv * P))
                for px in range(W):
                    for py in range(H):
                        # top / bottom: flip so text reads with the front at the bottom of the image
                        c = dict(face=face, px=px, py=py, W=W, H=H, name=n, group=g, box=b, rng=self.rng)
                        col = self.mat[m](c)
                        at = (ox + px, oy + py)
                        if col is not None:
                            bp[at] = tuple(clamp(t) for t in col[:3]) + ((clamp(col[3]),) if len(col) > 3 else (255,))
                        fn = self.glow.get(m)
                        if fn is not None:
                            gc = fn(c)
                            if gc is not None:
                                gp[at] = tuple(clamp(t) for t in gc[:3]) + ((clamp(gc[3]),) if len(gc) > 3 else (255,))
                        for k, fns in self.layers.items():
                            fn = fns.get(m)
                            if fn is not None:
                                gc = fn(c)
                                if gc is not None:
                                    lp[k][at] = tuple(clamp(t) for t in gc[:3]) + ((clamp(gc[3]),) if len(gc) > 3 else (255,))
        return self.base, self.glowimg

    def save(self, folder, name):
        """<name>.png, <name>_glow.png and one <name>_<suffix>.png per extra emissive layer."""
        os.makedirs(folder, exist_ok=True)
        self.base.save(os.path.join(folder, name + ".png"))
        self.glowimg.save(os.path.join(folder, name + "_glow.png"))
        for k, im in self.layer_imgs.items():
            im.save(os.path.join(folder, f"{name}_{k}.png"))

    # ------------------------------------------------------------ Java
    @staticmethod
    def f(v):
        return f"{round(float(v), 4)}F"

    def var(self, g):
        return "g_" + g

    def group_java(self, g):
        parent, piv, rot = self.groups[g]
        lines = []
        for (gg, n, b, m) in self.parts:
            if gg != g:
                continue
            x, y, z, w, h, d = b
            mx, my, mz = x, -(y + h), -(z + d)
            u, v = self.uv[n]
            gr = self.grow.get(n)
            dfm = f", new CubeDeformation({self.f(gr)})" if gr else ""
            lines.append(f"                .texOffs({u}, {v}).addBox({self.f(mx)}, {self.f(my)}, {self.f(mz)}, {self.f(w)}, {self.f(h)}, {self.f(d)}{dfm})   // {n}")
        px, py, pz = piv[0], -piv[1], -piv[2]
        rx, ry, rz = rot[0], -rot[1], -rot[2]
        if any(abs(r) > 1e-9 for r in (rx, ry, rz)):
            pose = f"PartPose.offsetAndRotation({self.f(px)}, {self.f(py)}, {self.f(pz)}, {self.f(rx)}, {self.f(ry)}, {self.f(rz)})"
        else:
            pose = f"PartPose.offset({self.f(px)}, {self.f(py)}, {self.f(pz)})"
        par = "root" if parent is None else self.var(parent)
        body = "\n".join(lines)
        return (f'        PartDefinition {self.var(g)} = {par}.addOrReplaceChild("{g}", CubeListBuilder.create()\n{body}\n'
                f'                , {pose});')

    def path(self, g):
        chain = []
        while g is not None:
            chain.append(g)
            g = self.groups[g][0]
        return "root" + "".join(f'.getChild("{x}")' for x in reversed(chain))

    def java(self, package, cls, layer, doc, fields, extra="", arrays=None, entity=None, setup=""):
        """fields: group names exposed as public ModelPart fields; arrays: {name: [groups]} exposed as ModelPart[].
        entity: a fully qualified entity class makes this an EntityModel of it, its setupAnim body being `setup`."""
        arrays = arrays or {}
        decl = ", ".join(["root"] + fields)
        assign = "\n".join(f"        this.{g} = {self.path(g)};" for g in fields)
        arr_decl = "\n".join(f"    public final ModelPart[] {a};" for a in arrays)
        arr_assign = "\n".join(f"        this.{a} = new ModelPart[]{{{', '.join(self.path(g) for g in gs)}}};" for a, gs in arrays.items())
        groups = "\n".join(self.group_java(g) for g in self.order)
        if entity:
            ent = entity.rsplit(".", 1)[1]
            base_import = f"import {entity};\nimport net.minecraft.client.model.EntityModel;"
            base = f"EntityModel<{ent}>"
            anim = f"""
    @Override
    public void setupAnim({ent} e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {{
{setup}
    }}
"""
        else:
            base_import = "import net.minecraft.client.model.Model;"
            base = "Model"
            anim = ""
        return f"""package {package};

import {MOD_IMPORT};
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
{base_import}
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
{doc}
 */
public class {cls} extends {base} {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation({MOD_CLASS}.MODID, "{layer}"), "main");
    public final ModelPart {decl};
{arr_decl}

    public {cls}(ModelPart root) {{
        super(RenderType::entityCutoutNoCull);
        this.root = root;
{assign}
{arr_assign}
    }}

    public static LayerDefinition createLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{groups}
        return LayerDefinition.create(mesh, {self.UVW}, {self.UVH});
    }}

    /** Everything back in its built pose, all shown. */
    public void reset() {{
        root.getAllParts().forEach(p -> {{
            p.resetPose();
            p.visible = true;
        }});
    }}
{extra}{anim}
    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {{
        root.render(pose, buffer, light, overlay, r, g, b, a);
    }}
}}
"""

    def layer_method(self, method, doc=""):
        """Just `public static LayerDefinition method()` building this rig (for a hand-written model class)."""
        groups = "\n".join(self.group_java(g) for g in self.order)
        d = f"    /** {doc} */\n" if doc else ""
        return f"""{d}    public static LayerDefinition {method}() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{groups}
        return LayerDefinition.create(mesh, {self.UVW}, {self.UVH});
    }}
"""

    # ------------------------------------------------------------ preview (ModelPart maths)
    @staticmethod
    def T(x, y, z):
        m = np.eye(4)
        m[:3, 3] = (x, y, z)
        return m

    @staticmethod
    def RX(a):
        c, s = math.cos(a), math.sin(a)
        m = np.eye(4)
        m[1, 1], m[1, 2], m[2, 1], m[2, 2] = c, -s, s, c
        return m

    @staticmethod
    def RY(a):
        c, s = math.cos(a), math.sin(a)
        m = np.eye(4)
        m[0, 0], m[0, 2], m[2, 0], m[2, 2] = c, s, -s, c
        return m

    @staticmethod
    def RZ(a):
        c, s = math.cos(a), math.sin(a)
        m = np.eye(4)
        m[0, 0], m[0, 1], m[1, 0], m[1, 1] = c, -s, s, c
        return m

    def group_matrix(self, g, anim):
        """anim: {group: (dx, dy, dz)} extra rotation in the TOOL frame (radians), as a renderer would set it."""
        chain = []
        while g is not None:
            chain.append(g)
            g = self.groups[g][0]
        M = np.eye(4)
        for gg in reversed(chain):
            piv, rot = self.groups[gg][1], self.groups[gg][2]
            a = anim.get(gg, (0.0, 0.0, 0.0))
            rx, ry, rz = rot[0] + a[0], -(rot[1] + a[1]), -(rot[2] + a[2])
            # an optional pivot shift (tool frame pixels) after the three angles
            tx, ty, tz = (a[3], a[4], a[5]) if len(a) >= 6 else (0.0, 0.0, 0.0)
            M = M @ self.T(piv[0] + tx, -(piv[1] + ty), -(piv[2] + tz)) @ self.RZ(rz) @ self.RY(ry) @ self.RX(rx)
        return M

    NORMALS = [(1, 0, 0), (-1, 0, 0), (0, -1, 0), (0, 1, 0), (0, 0, -1), (0, 0, 1)]   # east, west, down, up, north, south

    def cube_quads(self, name, box):
        """Model-space quads with their atlas UVs, in Minecraft's own vertex and UV order."""
        x, y, z, w, h, d = box
        mx, my, mz = x, -(y + h), -(z + d)
        u, v = self.uv[name]
        gr = self.grow.get(name, 0.0)
        X0, X1, Y0, Y1, Z0, Z1 = mx - gr, mx + w + gr, my - gr, my + h + gr, mz - gr, mz + d + gr
        V = [(X0, Y0, Z0), (X1, Y0, Z0), (X1, Y1, Z0), (X0, Y1, Z0), (X0, Y0, Z1), (X1, Y0, Z1), (X1, Y1, Z1), (X0, Y1, Z1)]
        f6, f7, f8, f9, f10, f11 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
        f12, f13, f14 = v, v + d, v + d + h
        polys = [((5, 1, 2, 6), (f8, f13, f10, f14)), ((0, 4, 7, 3), (f6, f13, f7, f14)), ((5, 4, 0, 1), (f7, f12, f8, f13)),
                 ((2, 3, 7, 6), (f8, f13, f9, f12)), ((1, 0, 3, 2), (f7, f13, f8, f14)), ((4, 5, 6, 7), (f10, f13, f11, f14))]
        out = []
        for idx, (u1, v1, u2, v2) in polys:
            uvs = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]
            out.append(([V[i] for i in idx], uvs))
        return out

    def preview(self, path, views, size=(480, 480), zoom=10.0, center=(0, 0, 0), bg=(26, 28, 32), glow_tint=(130, 235, 255),
                hidden=(), alpha_min=128, stride=1):
        """views: list of (yaw degrees, pitch degrees, anim dict, glow strength). Each view is drawn per texel, depth sorted."""
        base, glow = self.base, self.glowimg.copy()
        for im in self.layer_imgs.values():
            glow = Image.alpha_composite(glow, im)
        bpx, gpx = base.load(), glow.load()
        sheet = Image.new("RGB", (size[0] * len(views), size[1]), bg)
        FLIP = np.diag([1.0, -1.0, -1.0, 1.0])
        P = self.PX
        for vi, (yaw, pitch, anim, lit) in enumerate(views):
            img = Image.new("RGB", size, bg)
            d = ImageDraw.Draw(img)
            V = self.RX(math.radians(pitch)) @ self.RY(math.radians(yaw)) @ self.T(-center[0], -center[1], -center[2]) @ FLIP
            cells = []
            for (g, n, b, m) in self.parts:
                if g in hidden:
                    continue
                G = V @ self.group_matrix(g, anim)
                for (verts, uvs), nl in zip(self.cube_quads(n, b), self.NORMALS):
                    Pq = [(G @ np.array([vv[0], vv[1], vv[2], 1.0]))[:3] for vv in verts]
                    N = G[:3, :3] @ np.array(nl, float)
                    if N[2] <= 1e-6:       # we look along -z after the view turn: keep faces whose normal points at us
                        continue
                    (u2, v1), (u1, _), (_, v2), _ = uvs
                    TW = max(1, int(round(abs(u2 - u1) * P)))
                    TH = max(1, int(round(abs(v2 - v1) * P)))
                    p0, p1, p2, p3 = Pq
                    st = max(1, min(stride, TW, TH))
                    for i in range(0, TW, st):
                        for j in range(0, TH, st):
                            a0, a1 = i / TW, min(TW, i + st) / TW
                            b0, b1 = j / TH, min(TH, j + st) / TH

                            def L(a, bb):
                                top = p1 + (p0 - p1) * a
                                bot = p2 + (p3 - p2) * a
                                return top + (bot - top) * bb
                            # i runs from vertex 1 (u1) toward vertex 0 (u2), j from v1 toward v2: either can run backwards
                            tx = int(round(u1 * P)) + i if u2 > u1 else int(round(u1 * P)) - 1 - i
                            ty = int(round(v1 * P)) + j if v2 > v1 else int(round(v1 * P)) - 1 - j
                            if not (0 <= tx < base.width and 0 <= ty < base.height):
                                continue
                            col = bpx[tx, ty]
                            if col[3] < alpha_min:
                                continue
                            gl = gpx[tx, ty]
                            rgb = col[:3]
                            if gl[3] and lit > 0:
                                tint = glow_tint if glow_tint is not None else gl[:3]
                                k = lit * (max(gl[:3]) / 255.0 if glow_tint is None else gl[0] / 255.0)
                                rgb = tuple(min(255, int(rgb[q] + (tint[q] - rgb[q]) * k + (tint[q] * 0.35 * k if glow_tint is None else 0))) for q in range(3))
                            else:
                                k = 0.55 + 0.45 * max(0.0, float(N[2]))
                                rgb = tuple(int(c * k) for c in rgb)
                            q = [L(a0, b0), L(a1, b0), L(a1, b1), L(a0, b1)]
                            depth = sum(pp[2] for pp in q) / 4.0
                            cells.append((depth, [(size[0] / 2 + pp[0] * zoom, size[1] / 2 - pp[1] * zoom) for pp in q], rgb))
            cells.sort(key=lambda c: c[0])
            for _, poly, rgb in cells:
                d.polygon(poly, fill=rgb)
            sheet.paste(img, (vi * size[0], 0))
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        sheet.save(path)
        return path
