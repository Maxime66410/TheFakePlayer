package org.furranystudio.thefakeplayer.Entity.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

@SuppressWarnings({"unchecked", "rawtypes"})
public class FakePlayerArmorLayer extends RenderLayer<ArmedEntityRenderState, FakePlayerModelWithAnim<FakePlayerEntity>> {

    private final HumanoidArmorLayer armorLayer;
    private final HumanoidModel innerModel;
    private final HumanoidModel outerModel;

    public FakePlayerArmorLayer(
            RenderLayerParent<ArmedEntityRenderState, FakePlayerModelWithAnim<FakePlayerEntity>> parent,
            HumanoidModel<HumanoidRenderState> innerModel,
            HumanoidModel<HumanoidRenderState> outerModel,
            EquipmentLayerRenderer equipmentRenderer) {
        super(parent);
        this.innerModel = innerModel;
        this.outerModel = outerModel;

        // RenderLayerParent n'a qu'une méthode : getModel()
        // On retourne innerModel (déjà synchro avant render) — safe à runtime (erasure)
        RenderLayerParent fakeParent = new RenderLayerParent() {
            @Override
            public EntityModel getModel() { return innerModel; }
        };

        this.armorLayer = new HumanoidArmorLayer(fakeParent, innerModel, outerModel, equipmentRenderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       ArmedEntityRenderState renderState, float yRot, float xRot) {
        if (!(renderState instanceof HumanoidRenderState humanoidState)) return;
        FakePlayerModelWithAnim<?> ourModel = this.getParentModel();
        syncModelTransforms(ourModel, innerModel);
        syncModelTransforms(ourModel, outerModel);
        armorLayer.render(poseStack, bufferSource, packedLight, humanoidState, yRot, xRot);
    }

    private void syncModelTransforms(FakePlayerModelWithAnim<?> source, HumanoidModel target) {
        target.head.copyFrom(source.headPart());
        target.hat.copyFrom(source.headPart());
        target.body.copyFrom(source.torsoPart());
        target.rightArm.copyFrom(source.rightArmPart());
        target.leftArm.copyFrom(source.leftArmPart());
        target.rightLeg.copyFrom(source.rightLegPart());
        target.leftLeg.copyFrom(source.leftLegPart());
    }
}
