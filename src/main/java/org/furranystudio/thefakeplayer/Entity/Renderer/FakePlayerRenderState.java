package org.furranystudio.thefakeplayer.Entity.Renderer;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class FakePlayerRenderState extends HumanoidRenderState {
    public boolean isFishing = false;
    public boolean isCrouching = false;
    // Water target position relative to camera (computed in extractRenderState, used by line layer)
    public float fishingTargetCamX, fishingTargetCamY, fishingTargetCamZ;
}
