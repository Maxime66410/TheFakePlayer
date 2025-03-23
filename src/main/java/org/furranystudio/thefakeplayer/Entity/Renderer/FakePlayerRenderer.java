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
import org.furranystudio.thefakeplayer.Thefakeplayer;
import org.jetbrains.annotations.NotNull;

public class FakePlayerRenderer extends MobRenderer<FakePlayerEntity, LivingEntityRenderState,FakePlayerModelWithAnim<FakePlayerEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID, "textures/entities/basefakeplayer.png");

    public FakePlayerRenderer(EntityRendererProvider.Context p_174169_) {
        super(p_174169_, new FakePlayerModelWithAnim<>(p_174169_.bakeLayer(FakePlayerModelWithAnim.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public @NotNull HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(LivingEntityRenderState p_362468_) {
        return TEXTURE;
    }

    // Update the current texture of the fake player
    public void updateTexture() {
        
    }
}
