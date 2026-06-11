package org.furranystudio.thefakeplayer.Entity.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.util.Mth;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

public class FakePlayerFishingLineLayer extends RenderLayer<ArmedEntityRenderState, FakePlayerModelWithAnim<FakePlayerEntity>> {

    public FakePlayerFishingLineLayer(RenderLayerParent<ArmedEntityRenderState, FakePlayerModelWithAnim<FakePlayerEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       ArmedEntityRenderState renderState, float yRot, float xRot) {
        if (!(renderState instanceof FakePlayerRenderState fps) || !fps.isFishing) return;

        poseStack.pushPose();
        // Translate to the hand position (relative to entity origin)
        poseStack.translate(fps.fishingHandOffX, fps.fishingHandOffY, fps.fishingHandOffZ);

        VertexConsumer vc = buffer.getBuffer(RenderType.lineStrip());
        PoseStack.Pose pose = poseStack.last();

        // 16 segments — same catenary technique as vanilla FishingHookRenderer
        for (int i = 0; i <= 16; i++) {
            stringVertex(fps.fishingLineDX, fps.fishingLineDY, fps.fishingLineDZ,
                    vc, pose, i / 16f, (i + 1) / 16f);
        }

        poseStack.popPose();
    }

    // Straight line segment from hand to water target — no catenary offset needed here
    private static void stringVertex(float dx, float dy, float dz,
                                     VertexConsumer vc, PoseStack.Pose pose,
                                     float t0, float t1) {
        float x0 = dx * t0;
        float y0 = dy * t0;
        float z0 = dz * t0;
        float nx = dx * t1 - x0;
        float ny = dy * t1 - y0;
        float nz = dz * t1 - z0;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0f) { nx /= len; ny /= len; nz /= len; }
        vc.addVertex(pose, x0, y0, z0).setColor(-16777216).setNormal(pose, nx, ny, nz);
    }
}
