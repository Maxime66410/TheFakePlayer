package org.furranystudio.thefakeplayer.Handler;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.furranystudio.thefakeplayer.Entity.ModEntities;
import org.furranystudio.thefakeplayer.Entity.Renderer.FakePlayerModel;
import org.furranystudio.thefakeplayer.Entity.Renderer.FakePlayerRenderer;
import org.furranystudio.thefakeplayer.Thefakeplayer;

@Mod.EventBusSubscriber(modid = Thefakeplayer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModHandler {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
       event.enqueueWork(() -> {

       });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FAKE_PLAYER_ENTITY.get(), FakePlayerRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinition(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FakePlayerModel.LAYER_LOCATION, FakePlayerModel::createBodyLayer);
    }
}
