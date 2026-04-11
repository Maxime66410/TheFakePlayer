package org.furranystudio.thefakeplayer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.furranystudio.thefakeplayer.Thefakeplayer;

import java.util.UUID;

public record SyncSkinPacket(UUID entityUUID, String textureValue, String textureSignature, String skinUrl)
        implements CustomPacketPayload {

    public static final Type<SyncSkinPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "sync_skin")
    );

    public static final StreamCodec<FriendlyByteBuf, SyncSkinPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUUID(packet.entityUUID());
                buf.writeUtf(packet.textureValue());
                buf.writeUtf(packet.textureSignature());
                buf.writeUtf(packet.skinUrl());
            },
            buf -> new SyncSkinPacket(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
