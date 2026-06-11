package org.furranystudio.thefakeplayer.Entity.Renderer;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class FakePlayerRenderState extends HumanoidRenderState {
    public boolean isFishing = false;
    // Hand position offset relative to entity origin (world-space, computed in extractRenderState)
    public float fishingHandOffX, fishingHandOffY, fishingHandOffZ;
    // Vector from hand to water target (for line rendering)
    public float fishingLineDX, fishingLineDY, fishingLineDZ;
}
