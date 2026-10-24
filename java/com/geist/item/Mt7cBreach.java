package com.geist.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which blocks a breach thrust takes, layer by layer (the same answer on the server, which breaks them, and on the
 * client, which outlines them while you charge).
 *
 * Pickaxe: a tunnel one wide and two tall at your own height along the face you aim at (a 1x1 shaft when you aim
 * at a floor or a ceiling). It stops at the first thing it cannot take (a container, a fluid, bedrock, anything
 * too hard or the wrong kind of block) and when it breaks through into open space.
 * Shovel and hoe: a 3x3 face square to the aimed side, layer after layer; blocks they cannot take are just skipped.
 */
public final class Mt7cBreach {
    private static final int BREAK = 0, OPEN = 1, STOP = 2;

    private Mt7cBreach() {}

    @Nullable
    public static BlockHitResult aim(Level level, Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1.0F).scale(Mt7cItem.REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** Can this head take this block? */
    private static int classify(Level level, BlockPos pos, int mode) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return OPEN;
        if (!s.getFluidState().isEmpty() || s.hasBlockEntity()) return STOP;
        float hard = s.getDestroySpeed(level, pos);
        if (hard < 0.0F) return STOP;
        if (s.canBeReplaced()) return BREAK;                                     // grass, flowers, snow layers
        if (hard > Mt7cItem.MAX_HARDNESS[mode]) return STOP;
        boolean ok = mode == Mt7cItem.PICK
                ? s.is(BlockTags.MINEABLE_WITH_PICKAXE) || s.is(BlockTags.MINEABLE_WITH_SHOVEL)
                : s.is(Mt7cItem.mineable(mode));
        return ok ? BREAK : STOP;
    }

    public static boolean breakable(Level level, BlockPos pos, int mode) {
        return classify(level, pos, mode) == BREAK;
    }

    /** The blocks a breach of this depth takes, nearest layer first. Empty if nothing is aimed at or nothing can go. */
    public static List<List<BlockPos>> plan(Level level, Player p, int mode, int depth) {
        List<List<BlockPos>> layers = new ArrayList<>();
        BlockHitResult hit = depth > 0 ? aim(level, p) : null;
        if (hit == null) return layers;
        BlockPos at = hit.getBlockPos();
        Direction into = hit.getDirection().getOpposite();
        if (mode == Mt7cItem.PICK) {
            boolean shaft = into.getAxis().isVertical();
            int feet = p.getBlockY();
            int y0 = at.getY() == feet || at.getY() == feet + 1 ? feet : at.getY() > feet + 1 ? at.getY() - 1 : at.getY();
            for (int i = 0; i < depth; i++) {
                BlockPos c = at.relative(into, i);
                List<BlockPos> layer = new ArrayList<>(2);
                boolean blocked = false;
                for (int dy = 0; dy < (shaft ? 1 : 2); dy++) {
                    BlockPos q = shaft ? c : new BlockPos(c.getX(), y0 + dy, c.getZ());
                    int k = classify(level, q, mode);
                    if (k == BREAK) layer.add(q);
                    else if (k == STOP) blocked = true;
                }
                if (!layer.isEmpty()) layers.add(layer);
                if (blocked || (layer.isEmpty() && i > 0)) break;                // hit something it cannot take, or broke through
                if (layer.isEmpty()) break;
            }
        } else {
            Direction u = into.getAxis().isVertical() ? Direction.EAST : into.getClockWise();
            Direction v = into.getAxis().isVertical() ? Direction.SOUTH : Direction.UP;
            for (int i = 0; i < depth; i++) {
                List<BlockPos> layer = new ArrayList<>(9);
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        BlockPos q = at.relative(into, i).relative(u, a).relative(v, b);
                        if (classify(level, q, mode) == BREAK) layer.add(q);
                    }
                }
                if (layer.isEmpty()) break;
                layers.add(layer);
            }
        }
        return layers;
    }

    public static int count(List<List<BlockPos>> layers) {
        int n = 0;
        for (List<BlockPos> l : layers) n += l.size();
        return n;
    }
}
