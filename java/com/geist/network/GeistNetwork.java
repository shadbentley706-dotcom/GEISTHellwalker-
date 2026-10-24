package com.geist.network;

import com.geist.GeistMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class GeistNetwork {
    private static final String VERSION = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GeistMod.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private GeistNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, DifficultyPacket.class, DifficultyPacket::encode, DifficultyPacket::decode,
                DifficultyPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, PeekRequestPacket.class, PeekRequestPacket::encode, PeekRequestPacket::decode,
                PeekRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, PeekResultPacket.class, PeekResultPacket::encode, PeekResultPacket::decode,
                PeekResultPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, ExoActionPacket.class, ExoActionPacket::encode, ExoActionPacket::decode,
                ExoActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, ExoAnimPacket.class, ExoAnimPacket::encode, ExoAnimPacket::decode,
                ExoAnimPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, EquipVisorPacket.class, EquipVisorPacket::encode, EquipVisorPacket::decode,
                EquipVisorPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, GauntletActionPacket.class, GauntletActionPacket::encode, GauntletActionPacket::decode,
                GauntletActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(7, GauntletAnimPacket.class, GauntletAnimPacket::encode, GauntletAnimPacket::decode,
                GauntletAnimPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8, Mt7cActionPacket.class, Mt7cActionPacket::encode, Mt7cActionPacket::decode,
                Mt7cActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(9, Mt7cAnimPacket.class, Mt7cAnimPacket::encode, Mt7cAnimPacket::decode,
                Mt7cAnimPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(10, ZeroActionPacket.class, ZeroActionPacket::encode, ZeroActionPacket::decode,
                ZeroActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(11, ZeroAnimPacket.class, ZeroAnimPacket::encode, ZeroAnimPacket::decode,
                ZeroAnimPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
