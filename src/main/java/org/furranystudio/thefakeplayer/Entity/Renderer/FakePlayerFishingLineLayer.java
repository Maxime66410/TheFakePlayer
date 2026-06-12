package org.furranystudio.thefakeplayer.Entity.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class FakePlayerFishingLineLayer extends RenderLayer<ArmedEntityRenderState, FakePlayerModelWithAnim<FakePlayerEntity>> {

    private static final ResourceLocation BOBBER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType BOBBER_RENDER_TYPE = RenderType.entityCutout(BOBBER_TEXTURE);

    public FakePlayerFishingLineLayer(RenderLayerParent<ArmedEntityRenderState, FakePlayerModelWithAnim<FakePlayerEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       ArmedEntityRenderState renderState, float yRot, float xRot) {
        if (!(renderState instanceof FakePlayerRenderState fps) || !fps.isFishing) return;

        FakePlayerModelWithAnim<FakePlayerEntity> model = this.getParentModel();

        // --- Fishing line ---
        poseStack.pushPose();

        model.bodyPart().translateAndRotate(poseStack);
        model.rightArmPart().translateAndRotate(poseStack);
        poseStack.translate(-1.0f / 16.0f, 6.0f / 16.0f, -12.0f / 16.0f);

        Matrix4f invPose = new Matrix4f(poseStack.last().pose()).invert();
        Vector4f targetLocal = invPose.transform(
                new Vector4f(fps.fishingTargetCamX, fps.fishingTargetCamY, fps.fishingTargetCamZ, 1.0f));

        float dx = targetLocal.x;
        float dy = targetLocal.y;
        float dz = targetLocal.z;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len >= 0.001f) {
            float nx = dx / len, ny = dy / len, nz = dz / len;
            VertexConsumer vc = buffer.getBuffer(RenderType.lineStrip());
            PoseStack.Pose pose = poseStack.last();
            vc.addVertex(pose, 0.0f, 0.0f, 0.0f).setColor(-16777216).setNormal(pose, nx, ny, nz);
            vc.addVertex(pose, dx, dy, dz).setColor(-16777216).setNormal(pose, nx, ny, nz);
        }

        poseStack.popPose();

        // --- Bobber (billboard at water target, same approach as FishingHookRenderer) ---
        poseStack.pushPose();
        // Reset to camera-relative space so we can position absolutely
        poseStack.last().pose().identity();
        poseStack.last().normal().identity();
        poseStack.translate(fps.fishingTargetCamX + 1f / 16f, fps.fishingTargetCamY - 2f / 16f, fps.fishingTargetCamZ);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        PoseStack.Pose bobberPose = poseStack.last();
        VertexConsumer vc = buffer.getBuffer(BOBBER_RENDER_TYPE);
        vc.addVertex(bobberPose, -0.5f, -0.5f, 0f).setColor(-1).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(bobberPose, 0f, 1f, 0f);
        vc.addVertex(bobberPose,  0.5f, -0.5f, 0f).setColor(-1).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(bobberPose, 0f, 1f, 0f);
        vc.addVertex(bobberPose,  0.5f,  0.5f, 0f).setColor(-1).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(bobberPose, 0f, 1f, 0f);
        vc.addVertex(bobberPose, -0.5f,  0.5f, 0f).setColor(-1).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(bobberPose, 0f, 1f, 0f);

        poseStack.popPose();
    }
}
