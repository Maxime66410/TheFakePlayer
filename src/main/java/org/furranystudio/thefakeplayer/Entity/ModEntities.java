package org.furranystudio.thefakeplayer.Entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.furranystudio.thefakeplayer.Thefakeplayer;

import java.util.function.Supplier;


public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES
            = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Thefakeplayer.MODID);

    public static final RegistryObject<EntityType<FakePlayerEntity>> FAKE_PLAYER_ENTITY = ENTITIES.register("fake_player_entity",
            () -> EntityType.Builder.<FakePlayerEntity>of(FakePlayerEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .build(ResourceKey.create(ForgeRegistries.ENTITY_TYPES.getRegistryKey(), ResourceLocation.fromNamespaceAndPath(Thefakeplayer.MODID,"fake_player_entity")))
    );
}