package org.furranystudio.thefakeplayer.Events;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.furranystudio.thefakeplayer.Entity.ModEntities;
import org.furranystudio.thefakeplayer.Entity.ModMenuTypes;
import org.furranystudio.thefakeplayer.Entity.Renderer.FakePlayerInventoryScreen;
import org.furranystudio.thefakeplayer.Entity.Renderer.FakePlayerRenderer;
import org.furranystudio.thefakeplayer.Thefakeplayer;

@Mod.EventBusSubscriber(modid = Thefakeplayer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void entityClientRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FAKE_PLAYER_ENTITY.get(), FakePlayerRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
            MenuScreens.register(ModMenuTypes.FAKE_PLAYER_INVENTORY.get(), FakePlayerInventoryScreen::new)
        );
    }
}
