package org.furranystudio.thefakeplayer.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import org.furranystudio.thefakeplayer.Thefakeplayer;
import org.furranystudio.thefakeplayer.client.BuildVisualizerRenderer;

public class NetworkHandler {

    @SuppressWarnings("unchecked")
    private static Channel<CustomPacketPayload> CHANNEL;

    @SuppressWarnings("unchecked")
    public static void init() {
        CHANNEL = (Channel<CustomPacketPayload>) ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "network"))
                .optionalClient()
                .payloadChannel()
                .play()
                .clientbound()
                .add(BuildVisualizePacket.TYPE, BuildVisualizePacket.STREAM_CODEC,
                        (pkt, ctx) -> {
                            ctx.enqueueWork(() -> BuildVisualizerRenderer.applyPacket(pkt));
                            ctx.setPacketHandled(true);
                        })
                .build();
    }

    public static void sendVisualize(ServerPlayer player, BuildVisualizePacket packet) {
        CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }
}
