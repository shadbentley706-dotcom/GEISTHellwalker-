package com.geist.client.exo;

import com.geist.GeistMod;
import com.geist.item.ExoFrameItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import com.mojang.logging.LogUtils;
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
 * Third-person exo-frame animation on the player model (seen by everyone):
 * compress = a quick crouch; airborne = arms swept back, leading knee driven up, leaning into the leap;
 * landing = the three-point landing (crouched low, front knee up, back knee down, one hand planted, the
 * other arm out for balance); walking = a heavy dip on every footfall.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class ExoPoses {
    private static final Set<Integer> PUSHED = new HashSet<>();
    private static final float LANDING_DROP = 8.5F / 16.0F, LEAP_LEAN = 0.21F;

    private ExoPoses() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Custom arm poses (they also place the leg on the same side). Null if they could not be created. */
    private static HumanoidModel.ArmPose plantPose, bracePose, sweepPose;
    private static boolean posesOk;
    /** Player models the poses have already been tried on (one per skin type, plus any a mod swaps in). */
    private static final Set<Object> TESTED = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Creates the custom arm poses. Must run while the mod loads: Minecraft caches a lookup table of arm
     * poses the first time it draws a player, and a pose created after that crashes the game when used.
     */
    public static void bootstrap() {
        if (posesOk) return;
        try {
            plantPose = HumanoidModel.ArmPose.create("GEIST_EXO_PLANT", false, ExoPoses::plant);
            bracePose = HumanoidModel.ArmPose.create("GEIST_EXO_BRACE", false, ExoPoses::brace);
            sweepPose = HumanoidModel.ArmPose.create("GEIST_EXO_SWEEP", false, ExoPoses::sweep);
            posesOk = plantPose != null && bracePose != null && sweepPose != null;
        } catch (Throwable t) {
            posesOk = false;
            LOGGER.warn("G.E.I.S.T.: exo-frame arm poses unavailable, using a plain crouch instead", t);
        }
    }

    /**
     * Safety net: before a player model is ever drawn with the custom poses, try them on that very model
     * (with the player it is about to draw). If Minecraft or another mod cannot take them, the poses are
     * switched off for good - the leap and landing still crouch, lean and drop - instead of crashing.
     */
    private static boolean posesWork(PlayerModel<AbstractClientPlayer> model, Player p) {
        if (!posesOk) return false;
        if (TESTED.contains(model)) return true;
        if (!(p instanceof AbstractClientPlayer player)) return false;
        HumanoidModel.ArmPose right = model.rightArmPose, left = model.leftArmPose;
        try {
            model.rightArmPose = plantPose;
            model.leftArmPose = bracePose;
            model.setupAnim(player, 0.0F, 0.0F, player.tickCount, 0.0F, 0.0F);
            model.rightArmPose = sweepPose;
            model.leftArmPose = sweepPose;
            model.setupAnim(player, 0.0F, 0.0F, player.tickCount, 0.0F, 0.0F);
            TESTED.add(model);
        } catch (Throwable t) {
            posesOk = false;
            LOGGER.warn("G.E.I.S.T.: exo-frame arm poses failed a test run, using a plain crouch instead", t);
        } finally {
            model.rightArmPose = right;                            // the renderer poses the model again anyway
            model.leftArmPose = left;
        }
        return posesOk;
    }

    private static ModelPart arm(HumanoidModel<?> m, HumanoidArm side) {
        return side == HumanoidArm.RIGHT ? m.rightArm : m.leftArm;
    }

    private static ModelPart leg(HumanoidModel<?> m, HumanoidArm side) {
        return side == HumanoidArm.RIGHT ? m.rightLeg : m.leftLeg;
    }

    /** +1 rotates this side's limb away from the body. */
    private static float out(HumanoidArm side) {
        return side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
    }

    /** Landing: this hand planted on the ground in front (the crouch adds 0.4), this knee driven forward. */
    private static void plant(HumanoidModel<?> m, LivingEntity e, HumanoidArm side) {
        ModelPart a = arm(m, side), l = leg(m, side);
        a.xRot = -0.55F;
        a.yRot = 0.0F;
        a.zRot = 0.12F * out(side);
        l.xRot = -1.3F;
        l.yRot = 0.25F * out(side);
        l.zRot = 0.0F;
    }

    /** Landing: this arm thrown back and out for balance, this knee down behind. */
    private static void brace(HumanoidModel<?> m, LivingEntity e, HumanoidArm side) {
        ModelPart a = arm(m, side), l = leg(m, side);
        a.xRot = 0.7F;
        a.yRot = 0.0F;
        a.zRot = 0.75F * out(side);
        l.xRot = 1.25F;
        l.yRot = -0.1F * out(side);
        l.zRot = 0.0F;
    }

    /** Mid-leap: arms swept back, the leading knee up, the other leg trailing. */
    private static void sweep(HumanoidModel<?> m, LivingEntity e, HumanoidArm side) {
        boolean lead = (e instanceof Player p ? p.getMainArm() : HumanoidArm.RIGHT) == side;
        ModelPart a = arm(m, side), l = leg(m, side);
        a.xRot = 0.95F;
        a.yRot = 0.0F;
        a.zRot = 0.3F * out(side);
        l.xRot = lead ? -0.75F : 0.45F;
        l.yRot = 0.0F;
        l.zRot = 0.05F * out(side);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        ExoClient.Anim a = ExoClient.peek(p.getId());
        if (a == null) return;
        ItemStack exo = ExoFrameItem.worn(p);
        if (exo.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick();
        float t = (mc.level != null ? mc.level.getGameTime() : 0L) + pt;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        boolean rightHanded = p.getMainArm() == HumanoidArm.RIGHT;
        float lower = 0.0F, lean = 0.0F;
        switch (a.type) {
            case ExoClient.COMPRESS -> model.crouching = true;
            case ExoClient.AIRBORNE -> {
                if (posesWork(model, p)) {
                    model.rightArmPose = sweepPose;
                    model.leftArmPose = sweepPose;
                }
                lean = LEAP_LEAN;
            }
            case ExoClient.LANDING -> {
                model.crouching = true;
                if (posesWork(model, p)) {
                    model.rightArmPose = rightHanded ? plantPose : bracePose;
                    model.leftArmPose = rightHanded ? bracePose : plantPose;
                }
                float k = (t - a.start) / ExoFrameItem.LANDING_TICKS;
                float e = k < 0.15F ? k / 0.15F : (k > 0.75F ? (1.0F - k) / 0.25F : 1.0F);
                lower = LANDING_DROP * Mth.clamp(e, 0.0F, 1.0F);
            }
            default -> {
                float step = t - a.lastStep;                       // heavy gait: the body dips on each footfall
                if (step >= 0.0F && step < 4.0F) lower = 0.035F * (1.0F - step / 4.0F);
            }
        }
        if (lower == 0.0F && lean == 0.0F) return;
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
