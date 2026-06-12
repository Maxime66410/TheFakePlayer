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
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class FakePlayerFishingLineLayer extends RenderLayer<ArmedEntityRenderState, FakePlayerModelWithAnim<FakePlayerEntity>> {

    public FakePlayerFishingLineLayer(RenderLayerParent<ArmedEntityRenderState, FakePlayerModelWithAnim<FakePlayerEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       ArmedEntityRenderState renderState, float yRot, float xRot) {
        if (!(renderState instanceof FakePlayerRenderState fps) || !fps.isFishing) return;

        FakePlayerModelWithAnim<FakePlayerEntity> model = this.getParentModel();

        poseStack.pushPose();

        // Navigate model hierarchy to the rod tip (body → rightArm → hand offset)
        model.bodyPart().translateAndRotate(poseStack);
        model.rightArmPart().translateAndRotate(poseStack);
        // rod tip offset relative to rightArm in model units
        poseStack.translate(-1.0f / 16.0f, 6.0f / 16.0f, -12.0f / 16.0f);

        // Invert the current cumulative pose matrix to map camera-relative coords → current model space.
        // This handles entity rotation, renderer scale/flip, and arm pose in one step.
        Matrix4f invPose = new Matrix4f(poseStack.last().pose()).invert();
        Vector4f targetLocal = invPose.transform(
                new Vector4f(fps.fishingTargetCamX, fps.fishingTargetCamY, fps.fishingTargetCamZ, 1.0f));

        float dx = targetLocal.x;
        float dy = targetLocal.y;
        float dz = targetLocal.z;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001f) {
            poseStack.popPose();
            return;
        }
        float nx = dx / len, ny = dy / len, nz = dz / len;

        VertexConsumer vc = buffer.getBuffer(RenderType.lineStrip());
        PoseStack.Pose pose = poseStack.last();
        vc.addVertex(pose, 0.0f, 0.0f, 0.0f).setColor(-16777216).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, dx, dy, dz).setColor(-16777216).setNormal(pose, nx, ny, nz);

        poseStack.popPose();
    }
}
