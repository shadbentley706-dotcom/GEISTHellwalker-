package com.grapefruit.hellblazer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> specialist: something happened worth a moment on screen. */
public class EventPacket {
    /** a = the new rank. */
    public static final int RANK_UP = 0;
    /** text = the case, a = reputation, b = emeralds. */
    public static final int CLOSED = 1;
    /** text = the case. */
    public static final int FAILED = 2;
    /** text = why (full, items, levels), a/b = numbers for the message. */
    public static final int DENIED = 3;
    /** text = the case, a = its id. */
    public static final int ACCEPTED = 4;

    public final int kind, a, b;
    public final String text;

    public EventPacket(int kind, String text, int a, int b) {
        this.kind = kind;
        this.text = text;
        this.a = a;
        this.b = b;
    }

    public static void encode(EventPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeUtf(m.text, 256);
        buf.writeVarInt(m.a);
        buf.writeVarInt(m.b);
    }

    public static EventPacket decode(FriendlyByteBuf buf) {
        return new EventPacket(buf.readByte(), buf.readUtf(256), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EventPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.grapefruit.hellblazer.client.CaseHud.onEvent(m)));
        ctx.get().setPacketHandled(true);
    }
}
