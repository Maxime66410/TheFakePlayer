package org.furranystudio.thefakeplayer.Entity.Menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.furranystudio.thefakeplayer.Entity.ModMenuTypes;

public class FakePlayerInventoryMenu extends AbstractContainerMenu {

    public static final int EQUIP_END      = 5;
    public static final int FAKE_INV_START = 5;
    public static final int FAKE_INV_END   = 41;
    public static final int PLAYER_INV_START = 41;
    public static final int PLAYER_INV_END   = 77;

    // Server constructor
    public FakePlayerInventoryMenu(int id, Inventory playerInv, FakePlayerEntity entity) {
        super(ModMenuTypes.FAKE_PLAYER_INVENTORY.get(), id);
        buildSlots(playerInv, new EquipmentContainer(entity), entity.getInventory());
    }

    // Client constructor — called from IForgeMenuType factory, items synced from server
    public FakePlayerInventoryMenu(int id, Inventory playerInv, FriendlyByteBuf data) {
        super(ModMenuTypes.FAKE_PLAYER_INVENTORY.get(), id);
        buildSlots(playerInv, new SimpleContainer(5), new SimpleContainer(36));
    }

    private void buildSlots(Inventory playerInv, Container equip, Container inv) {
        // Equipment (slots 0-4)
        this.addSlot(new Slot(equip, 0, 8, 8));    // HEAD
        this.addSlot(new Slot(equip, 1, 8, 26));   // CHEST
        this.addSlot(new Slot(equip, 2, 8, 44));   // LEGS
        this.addSlot(new Slot(equip, 3, 8, 62));   // FEET
        this.addSlot(new Slot(equip, 4, 8, 80));   // OFFHAND

        // FakePlayer main inventory (slots 5-31) — SimpleContainer indices 9-35
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 110 + row * 18));
            }
        }

        // FakePlayer hotbar (slots 32-40) — SimpleContainer indices 0-8
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 168));
        }

        // Player main inventory (slots 41-67)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 206 + row * 18));
            }
        }

        // Player hotbar (slots 68-76)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 260));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem().copy();
        ItemStack original = stack.copy();

        if (index < FAKE_INV_END) {
            // FakePlayer slot → move to player inventory
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player slot → move to FakePlayer inventory
            if (!this.moveItemStackTo(stack, FAKE_INV_START, FAKE_INV_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return original;
    }

    // Maps the 5 equipment slots to FakePlayerEntity's EquipmentSlot API
    private static class EquipmentContainer implements Container {

        private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND
        };

        private final FakePlayerEntity entity;

        EquipmentContainer(FakePlayerEntity entity) {
            this.entity = entity;
        }

        @Override public int getContainerSize() { return 5; }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot s : SLOTS) if (!entity.getItemBySlot(s).isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return entity.getItemBySlot(SLOTS[index]);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack current = entity.getItemBySlot(SLOTS[index]);
            if (current.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = current.split(count);
            entity.setItemSlot(SLOTS[index], current.isEmpty() ? ItemStack.EMPTY : current);
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack current = entity.getItemBySlot(SLOTS[index]);
            entity.setItemSlot(SLOTS[index], ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            entity.setItemSlot(SLOTS[index], stack);
        }

        @Override public void setChanged() { }
        @Override public boolean stillValid(Player player) { return true; }

        @Override
        public void clearContent() {
            for (EquipmentSlot s : SLOTS) entity.setItemSlot(s, ItemStack.EMPTY);
        }
    }
}
