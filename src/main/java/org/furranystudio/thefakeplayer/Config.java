package org.furranystudio.thefakeplayer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = Thefakeplayer.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue BUILD_BLOCKS_PER_SECOND = BUILDER
        .comment("How many blocks the fake player places per second during construction (1-20)")
        .defineInRange("buildBlocksPerSecond", 10, 7, 20);

    public static final ForgeConfigSpec.IntValue NEW_BASE_DISTANCE = BUILDER
        .comment("Distance in blocks before the fake player decides to build a new base")
        .defineInRange("newBaseDistance", 1000, 64, 10000);

    public static final ForgeConfigSpec.IntValue ABANDON_BUILD_DISTANCE = BUILDER
        .comment("Distance in blocks at which the fake player abandons an in-progress construction")
        .defineInRange("abandonBuildDistance", 300, 32, 2000);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(ResourceLocation.parse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {

    }
}
