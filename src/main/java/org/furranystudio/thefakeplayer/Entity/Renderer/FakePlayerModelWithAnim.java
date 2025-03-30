package org.furranystudio.thefakeplayer.Entity.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.furranystudio.thefakeplayer.Thefakeplayer;

public class FakePlayerModelWithAnim<T extends FakePlayerEntity> extends EntityModel<ArmedEntityRenderState> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "fakeplayermodel"), "main");

	private final ModelParts parts;

    public FakePlayerModelWithAnim(ModelPart root) {
        super(root);
        ModelPart body = root.getChild("body");
        ModelPart head = body.getChild("head");
        ModelPart torso = body.getChild("torso");
        ModelPart leftArm = body.getChild("leftArm");
        ModelPart leftItem = leftArm.getChild("leftItem");
        ModelPart rightArm = body.getChild("rightArm");
        ModelPart rightItem = rightArm.getChild("rightItem");
        ModelPart leftLeg = body.getChild("leftLeg");
        ModelPart rightLeg = body.getChild("rightLeg");

		this.parts = new ModelParts(body, head, torso, leftArm, leftItem, rightArm, rightItem, leftLeg, rightLeg);
	}

	@Override
	public void setupAnim(ArmedEntityRenderState p_370046_) {
		super.setupAnim(p_370046_);
		this.root().getAllParts().forEach(ModelPart::resetPose);
		HumanoidModel.ArmPose humanoidmodel$armposeleft = p_370046_.leftArmPose;
		HumanoidModel.ArmPose humanoidmodel$armposeright = p_370046_.rightArmPose;

		// Look at direction or target
		this.getParts().head.xRot = p_370046_.xRot * (float) (Math.PI / 180.0);
		this.getParts().head.yRot = p_370046_.yRot * (float) (Math.PI / 180.0);

		float f1 = p_370046_.walkAnimationPos;
		float f2 = p_370046_.walkAnimationSpeed;

		// Walk animation
		this.getParts().leftLeg.xRot = Mth.cos(f1 * 0.6662F + (float) Math.PI) * 1.4F * f2;
		this.getParts().rightLeg.xRot = Mth.cos(f1 * 0.6662F) * 1.4F * f2;
		this.getParts().leftArm.xRot = Mth.cos(f1 * 0.6662F) * 1.4F * f2;
		this.getParts().rightArm.xRot = Mth.cos(f1 * 0.6662F + (float) Math.PI) * 1.4F * f2;
		this.getParts().leftItem.xRot = Mth.cos(f1 * 0.6662F) * 1.4F * f2;
		this.getParts().rightItem.xRot = Mth.cos(f1 * 0.6662F + (float) Math.PI) * 1.4F * f2;
		this.getParts().rightLeg.yRot = 0.005F;
		this.getParts().leftLeg.yRot = -0.005F;
		this.getParts().rightLeg.zRot = 0.005F;
		this.getParts().leftLeg.zRot = -0.005F;
	}


	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition torso = body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leftArm = body.addOrReplaceChild("leftArm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(5.0F, 2.0F, 0.0F));

		PartDefinition leftItem = leftArm.addOrReplaceChild("leftItem", CubeListBuilder.create().texOffs(0, 0).addBox(10.0F, 9.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-10.0F, 0.0F, 0.0F));

		PartDefinition rightArm = body.addOrReplaceChild("rightArm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(-5.0F, 2.0F, 0.0F));

		PartDefinition rightItem = rightArm.addOrReplaceChild("rightItem", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 10.0F, 0.0F, 1.5708F, 0.0F, 0.0F));

		PartDefinition leftLeg = body.addOrReplaceChild("leftLeg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(1.9F, 12.0F, 0.0F));

		PartDefinition rightLeg = body.addOrReplaceChild("rightLeg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(1, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(-1.9F, 12.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

    public ModelParts getParts() {
        return parts;
    }

	/*@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		this.parts.body.render(poseStack, vertexConsumer, packedLight, packedOverlay);
	}*/

	private record ModelParts(
			ModelPart body,
			ModelPart head,
			ModelPart torso,
			ModelPart leftArm,
			ModelPart leftItem,
			ModelPart rightArm,
			ModelPart rightItem,
			ModelPart leftLeg,
			ModelPart rightLeg
	) {}

	private void poseRightArm(T p_362371_, HumanoidModel.ArmPose p_366231_) {
		switch (p_366231_) {
			case EMPTY:
				this.getParts().rightArm.yRot = 0.0F;
				break;
			case ITEM:
				this.getParts().rightArm.xRot = this.getParts().rightArm.xRot * 0.5F - (float) (Math.PI / 10);
				this.getParts().rightArm.yRot = 0.0F;
				break;
			case BLOCK:
				this.poseBlockingArm(this.getParts().rightArm, true);
				break;
			case BOW_AND_ARROW:
				this.getParts().rightArm.yRot = -0.1F + this.getParts().head.yRot;
				this.getParts().leftArm.yRot = 0.1F + this.getParts().head.yRot + 0.4F;
				this.getParts().rightArm.xRot = (float) (-Math.PI / 2) + this.getParts().head.xRot;
				this.getParts().leftArm.xRot = (float) (-Math.PI / 2) + this.getParts().head.xRot;
				break;
			case THROW_SPEAR:
				this.getParts().rightArm.xRot = this.getParts().rightArm.xRot * 0.5F - (float) Math.PI;
				this.getParts().rightArm.yRot = 0.0F;
				break;
			case CROSSBOW_CHARGE:
				AnimationUtils.animateCrossbowCharge(this.getParts().rightArm, this.getParts().leftArm, p_362371_.maxCrossbowChargeDuration, p_362371_.ticksUsingItem, true);
				break;
			case CROSSBOW_HOLD:
				AnimationUtils.animateCrossbowHold(this.getParts().rightArm, this.getParts().leftArm, this.getParts().head, true);
				break;
			case SPYGLASS:
				this.getParts().rightArm.xRot = Mth.clamp(
						this.getParts().head.xRot - 1.9198622F - (p_362371_.isCrouching ? (float) (Math.PI / 12) : 0.0F), -2.4F, 3.3F
				);
				this.getParts().rightArm.yRot = this.getParts().head.yRot - (float) (Math.PI / 12);
				break;
			case TOOT_HORN:
				this.getParts().rightArm.xRot = Mth.clamp(this.getParts().head.xRot, -1.2F, 1.2F) - 1.4835298F;
				this.getParts().rightArm.yRot = this.getParts().head.yRot - (float) (Math.PI / 6);
				break;
			case BRUSH:
				this.getParts().rightArm.xRot = this.getParts().rightArm.xRot * 0.5F - (float) (Math.PI / 5);
				this.getParts().rightArm.yRot = 0.0F;
			default:
				//p_366231_.applyTransform(this, p_362371_, net.minecraft.world.entity.HumanoidArm.RIGHT);
		}
	}

	private void poseLeftArm(T p_363560_, HumanoidModel.ArmPose p_370002_) {
		switch (p_370002_) {
			case EMPTY:
				this.getParts().leftArm.yRot = 0.0F;
				break;
			case ITEM:
				this.getParts().leftArm.xRot = this.getParts().leftArm.xRot * 0.5F - (float) (Math.PI / 10);
				this.getParts().leftArm.yRot = 0.0F;
				break;
			case BLOCK:
				this.poseBlockingArm(this.getParts().leftArm, false);
				break;
			case BOW_AND_ARROW:
				this.getParts().rightArm.yRot = -0.1F + this.getParts().head.yRot - 0.4F;
				this.getParts().leftArm.yRot = 0.1F + this.getParts().head.yRot;
				this.getParts().rightArm.xRot = (float) (-Math.PI / 2) + this.getParts().head.xRot;
				this.getParts().leftArm.xRot = (float) (-Math.PI / 2) + this.getParts().head.xRot;
				break;
			case THROW_SPEAR:
				this.getParts().leftArm.xRot = this.getParts().leftArm.xRot * 0.5F - (float) Math.PI;
				this.getParts().leftArm.yRot = 0.0F;
				break;
			case CROSSBOW_CHARGE:
				AnimationUtils.animateCrossbowCharge(this.getParts().rightArm, this.getParts().leftArm, p_363560_.maxCrossbowChargeDuration, p_363560_.ticksUsingItem, false);
				break;
			case CROSSBOW_HOLD:
				AnimationUtils.animateCrossbowHold(this.getParts().rightArm, this.getParts().leftArm, this.getParts().head, false);
				break;
			case SPYGLASS:
				this.getParts().leftArm.xRot = Mth.clamp(
						this.getParts().head.xRot - 1.9198622F - (p_363560_.isCrouching ? (float) (Math.PI / 12) : 0.0F), -2.4F, 3.3F
				);
				this.getParts().leftArm.yRot = this.getParts().head.yRot + (float) (Math.PI / 12);
				break;
			case TOOT_HORN:
				this.getParts().leftArm.xRot = Mth.clamp(this.getParts().head.xRot, -1.2F, 1.2F) - 1.4835298F;
				this.getParts().leftArm.yRot = this.getParts().head.yRot + (float) (Math.PI / 6);
				break;
			case BRUSH:
				this.getParts().leftArm.xRot = this.getParts().leftArm.xRot * 0.5F - (float) (Math.PI / 5);
				this.getParts().leftArm.yRot = 0.0F;
			default:
				//p_370002_.applyTransform(this, p_363560_, net.minecraft.world.entity.HumanoidArm.LEFT);
		}
	}

	private void poseBlockingArm(ModelPart p_312070_, boolean p_311335_) {
		p_312070_.xRot = p_312070_.xRot * 0.5F - 0.9424779F + Mth.clamp(this.getParts().head.xRot, (float) (-Math.PI * 4.0 / 9.0), 0.43633232F);
		p_312070_.yRot = (p_311335_ ? -30.0F : 30.0F) * (float) (Math.PI / 180.0)
				+ Mth.clamp(this.getParts().head.yRot, (float) (-Math.PI / 6), (float) (Math.PI / 6));
	}

	protected void setupAttackAnimation(ArmedEntityRenderState p_367078_, float p_102859_) {
		if (!(p_102859_ <= 0.0F)) {
			ModelPart modelpart = this.getParts().rightArm;
			this.getParts().body.yRot = Mth.sin(Mth.sqrt(p_102859_) * (float) (Math.PI * 2)) * 0.2F;
			/*if (humanoidarm == HumanoidArm.LEFT) {
				this.getParts().body.yRot *= -1.0F;
			}*/

			float f2 = p_367078_.ageScale;
			this.getParts().rightArm.z = Mth.sin(this.getParts().body.yRot) * 5.0F * f2;
			this.getParts().rightArm.x = -Mth.cos(this.getParts().body.yRot) * 5.0F * f2;
			this.getParts().leftArm.z = -Mth.sin(this.getParts().body.yRot) * 5.0F * f2;
			this.getParts().leftArm.x = Mth.cos(this.getParts().body.yRot) * 5.0F * f2;
			this.getParts().rightArm.yRot = this.getParts().rightArm.yRot + this.getParts().body.yRot;
			this.getParts().leftArm.yRot = this.getParts().leftArm.yRot + this.getParts().body.yRot;
			this.getParts().leftArm.xRot = this.getParts().leftArm.xRot + this.getParts().body.yRot;
			float $$5 = 1.0F - p_102859_;
			$$5 *= $$5;
			$$5 *= $$5;
			$$5 = 1.0F - $$5;
			float f3 = Mth.sin($$5 * (float) Math.PI);
			float f4 = Mth.sin(p_102859_ * (float) Math.PI) * -(this.getParts().head.xRot - 0.7F) * 0.75F;
			modelpart.xRot -= f3 * 1.2F + f4;
			modelpart.yRot = modelpart.yRot + this.getParts().body.yRot * 2.0F;
			modelpart.zRot = modelpart.zRot + Mth.sin(p_102859_ * (float) Math.PI) * -0.4F;
		}
	}
}