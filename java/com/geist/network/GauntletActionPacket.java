package com.geist.network;

import com.geist.event.GauntletCarry;
import com.geist.event.GauntletEvents;
import com.geist.event.GauntletGuard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: what the player is asking the gauntlet to do.
 * BARRAGE_START / BARRAGE_STOP: the barrage key or a held attack. CHARGE: started winding up a throw of the mob
 * they are carrying (use held). THROW: let go of use. SLAM: attacked while carrying. GUARD_UP / GUARD_DOWN: use held
 * / let go with both hands empty.
 */
public class GauntletActionPacket {
    public static final int BARRAGE_START = 0, BARRAGE_STOP = 1, CHARGE = 2, THROW = 3, SLAM = 4, GUARD_UP = 5, GUARD_DOWN = 6;
    private final int action;

    public GauntletActionPacket(int action) {
        this.action = action;
    }

    public GauntletActionPacket(boolean barrageStart) {
        this(barrageStart ? BARRAGE_START : BARRAGE_STOP);
    }

    public static void encode(GauntletActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
    }

    public static GauntletActionPacket decode(FriendlyByteBuf buf) {
        return new GauntletActionPacket(buf.readVarInt());
    }

    public static void handle(GauntletActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            switch (msg.action) {
                case BARRAGE_START -> GauntletEvents.request(p, true);
                case BARRAGE_STOP -> GauntletEvents.request(p, false);
                case CHARGE -> GauntletCarry.charge(p);
                case THROW -> GauntletCarry.release(p);
                case SLAM -> GauntletCarry.slam(p);
                case GUARD_UP -> GauntletGuard.raise(p);
                case GUARD_DOWN -> GauntletGuard.lower(p);
                default -> { }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
