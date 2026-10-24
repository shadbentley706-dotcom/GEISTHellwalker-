package com.geist.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> the wearer and everyone tracking them: something happened to their gauntlet.
 * WINDUP / BARRAGE / COOLDOWN (a = lock-out ticks) / CANCEL track the barrage, PUNCH (a = arm 0 right or 1 left,
 * b = 1 if it landed), HEAVY is a landed normal punch.
 * Carrying: CARRY (a = the lifted mob's entity id), CHARGE (winding up a throw), THROW (a = lock-out ticks, b = the
 * mob, now in flight), SLAM (a = lock-out ticks, b = the mob), DROP (a = lock-out ticks, b = the mob: it got loose).
 * Guard: GUARD (a = 1 raised, 0 lowered), BLOCK (a = what was blocked: 0 a hit, 1 a projectile, 2 the Warden's boom,
 * plus 10 for a parry; b = the guard's heat 0..100), GUARD_BREAK (a = ticks before it can go up again).
 */
public class GauntletAnimPacket {
    public static final int WINDUP = 0, BARRAGE = 1, PUNCH = 2, COOLDOWN = 3, CANCEL = 4, HEAVY = 5, THROW = 6,
            CARRY = 7, CHARGE = 8, SLAM = 9, DROP = 10,
            GUARD = 11, BLOCK = 12, GUARD_BREAK = 13;
    private final int entityId, event, a, b;

    public GauntletAnimPacket(int entityId, int event, int a, int b) {
        this.entityId = entityId;
        this.event = event;
        this.a = a;
        this.b = b;
    }

    public static void encode(GauntletAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.event);
        buf.writeVarInt(msg.a);
        buf.writeVarInt(msg.b);
    }

    public static GauntletAnimPacket decode(FriendlyByteBuf buf) {
        return new GauntletAnimPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(GauntletAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.geist.client.gauntlet.GauntletClient.remote(msg.entityId, msg.event, msg.a, msg.b)));
        ctx.get().setPacketHandled(true);
    }
}
