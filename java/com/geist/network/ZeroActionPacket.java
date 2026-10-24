package com.geist.network;

import com.geist.item.ZeroLightItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the Zero Light mode key was pressed (MODE: standard -> shockwave -> ricochet). */
public class ZeroActionPacket {
    public static final int MODE = 0;
    private final int action;

    public ZeroActionPacket(int action) {
        this.action = action;
    }

    public static void encode(ZeroActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
    }

    public static ZeroActionPacket decode(FriendlyByteBuf buf) {
        return new ZeroActionPacket(buf.readVarInt());
    }

    public static void handle(ZeroActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && msg.action == MODE) ZeroLightItem.switchMode(p);
        });
        ctx.get().setPacketHandled(true);
    }
}
