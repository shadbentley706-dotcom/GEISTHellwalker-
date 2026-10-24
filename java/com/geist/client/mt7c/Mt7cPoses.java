package com.geist.client.mt7c;

import com.geist.GeistMod;
import com.geist.item.Mt7cItem;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Third-person MT-7C poses (seen by everyone): two-handed brace while charging (the off hand on the receiver, the
 * tool pulled back and aimed where they look), both arms driving forward on the thrust, and the tool brought
 * across the body with the off hand on the collar during a mode change.
 */
@Mod.EventBusSubscriber(modid = GeistMod.MODID, value = Dist.CLIENT)
public final class Mt7cPoses {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Object> TESTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Mt7cAnim.Pose POSE = new Mt7cAnim.Pose();
    private static HumanoidModel.ArmPose pose;
    private static boolean poseOk;
    private static float drawTime, drawPt;

    private Mt7cPoses() {}

    /** Must run while the mod loads (an arm pose made later crashes the game the first time it is used). */
    public static void bootstrap() {
        if (poseOk) return;
        try {
            pose = HumanoidModel.ArmPose.create("GEIST_MT7C", false, Mt7cPoses::apply);
            poseOk = pose != null;
        } catch (Throwable t) {
            poseOk = false;
            LOGGER.warn("G.E.I.S.T.: MT-7C arm pose unavailable, the arms keep their normal hold", t);
        }
    }

    private static boolean poseWorks(PlayerModel<AbstractClientPlayer> model, Player p) {
        if (!poseOk) return false;
        if (TESTED.contains(model)) return true;
        if (!(p instanceof AbstractClientPlayer player)) return false;
        HumanoidModel.ArmPose right = model.rightArmPose, left = model.leftArmPose;
        try {
            model.rightArmPose = pose;
            model.leftArmPose = pose;
            model.setupAnim(player, 0.0F, 0.0F, player.tickCount, 0.0F, 0.0F);
            TESTED.add(model);
        } catch (Throwable t) {
            poseOk = false;
            LOGGER.warn("G.E.I.S.T.: MT-7C arm pose failed a test run, the arms keep their normal hold", t);
        } finally {
            model.rightArmPose = right;
            model.leftArmPose = left;
        }
        return poseOk;
    }

    private static void apply(HumanoidModel<?> m, LivingEntity e, HumanoidArm side) {
        Mt7cClient.Anim a = Mt7cClient.peek(e.getId());
        int mode = Mt7cItem.mode(e.getMainHandItem());
        Mt7cAnim.Pose p = Mt7cAnim.pose(POSE, a, mode, Mt7cAnim.charge(e, drawPt), drawTime);
        boolean main = (e instanceof Player pl ? pl.getMainArm() : HumanoidArm.RIGHT) == side;
        ModelPart arm = side == HumanoidArm.RIGHT ? m.rightArm : m.leftArm;
        float in = side == HumanoidArm.RIGHT ? -1.0F : 1.0F;                       // yRot toward the body's middle
        float aim = m.head.xRot * 0.6F;
        float c = Mt7cAnim.ease(p.charge), l = p.lunge;
        float x, y, z = 0.0F, push;
        if (p.switchK >= 0.0F) {                                                  // turning the collar
            float k = Mth.sin((float) Math.PI * p.switchK);
            x = main ? -0.75F : -0.95F;
            y = in * (main ? 0.45F : 0.75F) + in * 0.1F * k;
            push = 0.0F;
        } else {
            x = (main ? -1.0F - 0.25F * c : -1.2F - 0.15F * c) + aim;             // braced, aimed where they look
            y = in * (main ? 0.1F : 0.55F);
            push = main ? 1.2F * c : 0.8F * c;
            x = Mth.lerp(l, x, -1.5F + aim);                                       // the drive
            push = Mth.lerp(l, push, main ? -2.0F : -1.5F);
        }
        arm.xRot = x;
        arm.yRot = y;
        arm.zRot = z;
        arm.z += push;
    }

    /** Is a player doing something with the MT-7C that needs the custom hold? */
    private static boolean active(Player p, float t) {
        if (!(p.getMainHandItem().getItem() instanceof Mt7cItem)) return false;
        if (Mt7cAnim.charging(p)) return true;
        Mt7cClient.Anim a = Mt7cClient.peek(p.getId());
        if (a == null) return false;
        return (t - a.thrustAt >= 0.0F && t - a.thrustAt < Mt7cAnim.THRUST_TICKS)
                || (t - a.switchAt >= 0.0F && t - a.switchAt < Mt7cItem.SWITCH_TICKS);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick();
        float t = (mc.level != null ? mc.level.getGameTime() : 0L) + pt;
        if (!active(p, t)) return;
        drawTime = t;
        drawPt = pt;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        if (poseWorks(model, p)) {
            model.rightArmPose = pose;
            model.leftArmPose = pose;
        }
    }
}
