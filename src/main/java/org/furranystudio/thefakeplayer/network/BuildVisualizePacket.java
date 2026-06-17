package org.furranystudio.thefakeplayer.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.furranystudio.thefakeplayer.Thefakeplayer;

import java.util.ArrayList;
import java.util.List;

public record BuildVisualizePacket(boolean enabled, List<BlockPos> pending, List<BlockPos> missing)
        implements CustomPacketPayload {

    public static final Type<BuildVisualizePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "build_visualize")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildVisualizePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBoolean(pkt.enabled());
                buf.writeVarInt(pkt.pending().size());
                for (BlockPos pos : pkt.pending()) buf.writeBlockPos(pos);
                buf.writeVarInt(pkt.missing().size());
                for (BlockPos pos : pkt.missing()) buf.writeBlockPos(pos);
            },
            buf -> {
                boolean enabled = buf.readBoolean();
                int n = buf.readVarInt();
                List<BlockPos> pending = new ArrayList<>(n);
                for (int i = 0; i < n; i++) pending.add(buf.readBlockPos());
                int m = buf.readVarInt();
                List<BlockPos> missing = new ArrayList<>(m);
                for (int i = 0; i < m; i++) missing.add(buf.readBlockPos());
                return new BuildVisualizePacket(enabled, pending, missing);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
