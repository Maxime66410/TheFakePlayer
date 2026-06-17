package org.furranystudio.thefakeplayer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.furranystudio.thefakeplayer.network.BuildVisualizePacket;

import javax.annotation.Nullable;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class BuildVisualizerRenderer {

    @Nullable
    private static List<BlockPos> pendingPositions = null;
    @Nullable
    private static List<BlockPos> missingPositions = null;

    public static void applyPacket(BuildVisualizePacket packet) {
        if (!packet.enabled()) {
            pendingPositions = null;
            missingPositions = null;
            return;
        }
        pendingPositions = packet.pending();
        missingPositions = packet.missing();
    }

    // Called from MixinDebugRenderer every frame (camera at origin in the PoseStack)
    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                              double camX, double camY, double camZ) {
        if (pendingPositions == null && missingPositions == null) return;

        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);

        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        if (pendingPositions != null) {
            for (BlockPos pos : pendingPositions) {
                // White outline: block to place (item available)
                ShapeRenderer.renderLineBox(poseStack, lines,
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                        1f, 1f, 1f, 0.8f);
            }
        }

        if (missingPositions != null) {
            for (BlockPos pos : missingPositions) {
                // Red outline: block to place (item missing from inventory)
                ShapeRenderer.renderLineBox(poseStack, lines,
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                        1f, 0.2f, 0.2f, 0.8f);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }
}
