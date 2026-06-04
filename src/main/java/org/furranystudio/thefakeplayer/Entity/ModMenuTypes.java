package org.furranystudio.thefakeplayer.Entity;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.furranystudio.thefakeplayer.Entity.Menu.FakePlayerInventoryMenu;
import org.furranystudio.thefakeplayer.Thefakeplayer;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Thefakeplayer.MODID);

    public static final RegistryObject<MenuType<FakePlayerInventoryMenu>> FAKE_PLAYER_INVENTORY =
            MENU_TYPES.register("fake_player_inventory",
                    () -> IForgeMenuType.create(FakePlayerInventoryMenu::new));
}
