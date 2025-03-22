package org.furranystudio.thefakeplayer.Entity.Renderer;


import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.jetbrains.annotations.NotNull;

public class FakePlayerRenderer extends HumanoidMobRenderer<FakePlayerEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    public FakePlayerRenderer(EntityRendererProvider.Context p_174169_) {
        super(p_174169_, new HumanoidModel<>(p_174169_.bakeLayer(FakePlayerModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull HumanoidRenderState p_362468_) {
        return ResourceLocation.withDefaultNamespace("textures/entity/steve.png");
    }

    @Override
    public @NotNull HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }
}
