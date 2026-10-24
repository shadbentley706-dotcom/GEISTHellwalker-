package com.geist.client.gauntlet;

import com.geist.GeistMod;
import com.geist.item.GauntletItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Third-person gauntlet animation on the player model (seen by everyone): fists up in a boxer's guard, the
 * punches thrown alternately with the shoulders following, the arms sagging as the steam vents, and the
 * football throw of a carried mob. The arm angles come from {@link GauntletAnimator}; this class puts them on the
 * model through a custom arm pose, and adds the body lean and the dip.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class GauntletPoses {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Integer> PUSHED = new HashSet<>();
    private static final Set<Object> TESTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final GauntletAnimator.Out OUT = new GauntletAnimator.Out();

    /** The pose used for both arms. Null if it could not be created. */
    private static HumanoidModel.ArmPose pose;
    private static boolean posesOk;
    /** The time (ticks plus partial tick) of the player being drawn right now: the pose callback has no other way to know. */
    private static float drawTime;

    private GauntletPoses() {}

    /**
     * Creates the custom arm pose. Must run while the mod loads: Minecraft caches a lookup table of arm
     * poses the first time it draws a player, and a pose created after that crashes the game when used.
     */
    public static void bootstrap() {
        if (posesOk) return;
        try {
            pose = HumanoidModel.ArmPose.create("GEIST_GAUNTLET", false, GauntletPoses::apply);
            posesOk = pose != null;
        } catch (Throwable t) {
            posesOk = false;
            LOGGER.warn("G.E.I.S.T.: gauntlet arm pose unavailable, the arms keep their normal swing", t);
        }
    }

    /** Safety net: try the pose on the very model about to be drawn; if it throws, switch it off for good. */
    private static boolean poseWorks(PlayerModel<AbstractClientPlayer> model, Player p) {
        if (!posesOk) return false;
        if (TESTED.contains(model)) return true;
        if (!(p instanceof AbstractClientPlayer player)) return false;
        HumanoidModel.ArmPose right = model.rightArmPose, left = model.leftArmPose;
        try {
            model.rightArmPose = pose;
            model.leftArmPose = pose;
            model.setupAnim(player, 0.0F, 0.0F, player.tickCount, 0.0F, 0.0F);
            TESTED.add(model);
        } catch (Throwable t) {
            posesOk = false;
            LOGGER.warn("G.E.I.S.T.: gauntlet arm pose failed a test run, the arms keep their normal swing", t);
        } finally {
            model.rightArmPose = right;
            model.leftArmPose = left;
        }
        return posesOk;
    }

    /** Called by the model for each arm after the vanilla swing: blend towards the gauntlet pose. */
    private static void apply(HumanoidModel<?> m, LivingEntity e, HumanoidArm side) {
        GauntletClient.Anim an = GauntletClient.peek(e.getId());
        boolean right = side == HumanoidArm.RIGHT;
        GauntletAnimator.Out o = OUT;
        GauntletAnimator.eval(an, right ? 0 : 1, drawTime, o);
        ModelPart arm = right ? m.rightArm : m.leftArm;
        float w = o.w;
        arm.xRot = Mth.lerp(w, arm.xRot, o.x);
        arm.yRot = Mth.lerp(w, arm.yRot, o.y);
        arm.zRot = Mth.lerp(w, arm.zRot, o.z);
        arm.z += o.push * w;
        // the shoulders turn into the punch: the body twists and the arms swing round with it
        float tw = GauntletAnimator.twist(an, drawTime);
        if (tw != 0.0F) {
            m.body.yRot = tw;
            float s = Mth.sin(tw) * 5.0F, c = Mth.cos(tw) * 5.0F;
            arm.z += right ? s : -s;
            arm.x = right ? -c : c;
            arm.yRot += tw;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        ItemStack g = GauntletItem.worn(p);
        if (g.isEmpty()) return;
        GauntletClient.Anim a = GauntletClient.peek(p.getId());
        if (a == null) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick();
        float t = (mc.level != null ? mc.level.getGameTime() : 0L) + pt;
        drawTime = t;
        if (!GauntletAnimator.busy(a, t)) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        if (GauntletAnimator.crouch(a, t)) model.crouching = true;
        if (poseWorks(model, p)) {
            model.rightArmPose = pose;
            model.leftArmPose = pose;
        }
        float lean = GauntletAnimator.lean(a, t), lower = GauntletAnimator.lower(a, t);
        if (lean == 0.0F && lower == 0.0F) return;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        PUSHED.add(p.getId());
        ps.translate(0.0D, -lower, 0.0D);
        if (lean != 0.0F) {                                        // lean forward around the hips
            float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
            ps.translate(0.0D, 0.75D, 0.0D);
            ps.mulPose(new Quaternionf().rotateAxis(lean, Mth.cos(yaw), 0.0F, Mth.sin(yaw)));
            ps.translate(0.0D, -0.75D, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (PUSHED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }
}
