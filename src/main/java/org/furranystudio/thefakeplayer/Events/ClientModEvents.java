package org.furranystudio.thefakeplayer.Events;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.furranystudio.thefakeplayer.Entity.ModEntities;
import org.furranystudio.thefakeplayer.Entity.Renderer.FakePlayerRenderer;
import org.furranystudio.thefakeplayer.Thefakeplayer;

@Mod.EventBusSubscriber(modid = Thefakeplayer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void entityClientRederer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FAKE_PLAYER_ENTITY.get(), FakePlayerRenderer::new);
    }
}
