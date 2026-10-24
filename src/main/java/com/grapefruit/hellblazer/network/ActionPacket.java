package com.grapefruit.hellblazer.network;

import com.grapefruit.hellblazer.occult.Cases;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Specialist -> server: an order from the casebook (take a case, drop one, send in relics, new leads). */
public class ActionPacket {
    public static final int ACCEPT = 0, ABANDON = 1, DELIVER = 2, REROLL = 3;
    private final int action, arg;

    public ActionPacket(int action, int arg) {
        this.action = action;
        this.arg = arg;
    }

    public static void encode(ActionPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.action);
        buf.writeVarInt(m.arg);
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        return new ActionPacket(buf.readByte(), buf.readVarInt());
    }

    public static void handle(ActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null || p.isSpectator()) return;
            switch (m.action) {
                case ACCEPT -> Cases.accept(p, m.arg);
                case ABANDON -> Cases.abandon(p, m.arg);
                case DELIVER -> Cases.deliver(p, m.arg);
                case REROLL -> Cases.reroll(p);
                default -> {
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
