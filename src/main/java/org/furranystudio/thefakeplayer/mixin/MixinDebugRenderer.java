package org.furranystudio.thefakeplayer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.furranystudio.thefakeplayer.client.BuildVisualizerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    @Inject(method = "render", at = @At("RETURN"))
    private void thefakeplayer$renderVisualize(PoseStack poseStack, Frustum frustum,
            MultiBufferSource.BufferSource bufferSource,
            double camX, double camY, double camZ, CallbackInfo ci) {
        BuildVisualizerRenderer.render(poseStack, bufferSource, camX, camY, camZ);
    }
}
