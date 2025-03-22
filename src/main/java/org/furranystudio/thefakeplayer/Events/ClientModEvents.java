package org.furranystudio.thefakeplayer.Events;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.ForgePacketHandler;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.furranystudio.thefakeplayer.Entity.ModEntities;
import org.furranystudio.thefakeplayer.Entity.Renderer.FakePlayerRenderer;
import org.furranystudio.thefakeplayer.Thefakeplayer;

@Mod.EventBusSubscriber(modid = Thefakeplayer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    // permet de build les attributs de l'entité au lancement du jeu
    @SubscribeEvent
    public static void entityAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.FAKE_PLAYER_ENTITY.get(), FakePlayerEntity.createFakePlayerAttributes().build());
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event)
    {
        event.register(
                ModEntities.FAKE_PLAYER_ENTITY.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.WORLD_SURFACE,
                FakePlayerEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.OR
        );
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> {

        });
    }
}
