package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.furranystudio.thefakeplayer.Entity.Build.HardcodedShelterBuilder;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;
import java.util.function.Predicate;

public class FakePlayerOrganizeInventoryGoal extends Goal {

    // Preferred inventory slot for each category
    private static final int SLOT_WEAPON = 0;
    private static final int SLOT_TOOL   = 1;
    private static final int SLOT_FOOD   = 2;
    private static final int SLOT_TORCH  = 3;
    private static final int SLOT_BOW    = 4;
    private static final int SLOT_ARROWS = 5;

    // Items dropped when inventory is full (low-value blocks)
    private static final int JUNK_DROP_THRESHOLD = 32;

    private final FakePlayerEntity entity;
    private int cooldown = 0;

    public FakePlayerOrganizeInventoryGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (entity.getTarget() != null) return false;
        if (--cooldown > 0) return false;
        cooldown = 200 + entity.getRandom().nextInt(100);
        return true;
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        mergePartialStacks();
        dropJunk();
        organizeSlots();
    }

    // --- Organize preferred slots ---

    private void organizeSlots() {
        int bestWeapon = findBestWeaponSlot();
        if (bestWeapon >= 0) swapToTarget(SLOT_WEAPON, bestWeapon);

        placeFirstMatch(SLOT_TOOL,   s -> s.getItem() instanceof PickaxeItem);
        placeFirstMatch(SLOT_FOOD,   s -> s.has(DataComponents.FOOD));
        placeFirstMatch(SLOT_TORCH,  s -> s.getItem() == Items.TORCH || s.getItem() == Items.SOUL_TORCH);
        placeFirstMatch(SLOT_BOW,    s -> s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem);
        placeFirstMatch(SLOT_ARROWS, s -> s.getItem() instanceof ArrowItem);
    }

    private int findBestWeaponSlot() {
        SimpleContainer inv = entity.getInventory();
        int bestSlot = -1;
        float bestScore = getAttackDamage(inv.getItem(SLOT_WEAPON));
        for (int i = 1; i < inv.getContainerSize(); i++) {
            float score = getAttackDamage(inv.getItem(i));
            if (score > bestScore) { bestScore = score; bestSlot = i; }
        }
        return bestSlot;
    }

    private float getAttackDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().value() == Attributes.ATTACK_DAMAGE.value()) return (float) e.modifier().amount();
        }
        return 0f;
    }

    private void placeFirstMatch(int targetSlot, Predicate<ItemStack> pred) {
        SimpleContainer inv = entity.getInventory();
        if (!inv.getItem(targetSlot).isEmpty() && pred.test(inv.getItem(targetSlot))) return;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i == targetSlot) continue;
            if (!inv.getItem(i).isEmpty() && pred.test(inv.getItem(i))) {
                swapToTarget(targetSlot, i);
                return;
            }
        }
    }

    private void swapToTarget(int target, int source) {
        SimpleContainer inv = entity.getInventory();
        ItemStack a = inv.getItem(target).copy();
        inv.setItem(target, inv.getItem(source).copy());
        inv.setItem(source, a);
    }

    // --- Merge partial stacks ---

    private void mergePartialStacks() {
        SimpleContainer inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack base = inv.getItem(i);
            if (base.isEmpty() || base.getCount() >= base.getMaxStackSize()) continue;
            for (int j = i + 1; j < inv.getContainerSize(); j++) {
                ItemStack other = inv.getItem(j);
                if (other.isEmpty() || !ItemStack.isSameItemSameComponents(base, other)) continue;
                int space = base.getMaxStackSize() - base.getCount();
                int transfer = Math.min(space, other.getCount());
                base.grow(transfer);
                other.shrink(transfer);
                if (other.isEmpty()) inv.setItem(j, ItemStack.EMPTY);
                if (base.getCount() >= base.getMaxStackSize()) break;
            }
        }
    }

    // --- Drop junk when inventory is full ---

    private void dropJunk() {
        if (!isInventoryAlmostFull()) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        boolean buildingActive = entity.getActiveTask() != null && !entity.getActiveTask().isAbandoned();
        SimpleContainer inv = entity.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) return; // free slot found, no need to drop
            if (isJunk(stack) && stack.getCount() > JUNK_DROP_THRESHOLD) {
                // Keep build materials while a construction is in progress
                if (buildingActive && HardcodedShelterBuilder.isBuildMaterial(stack.getItem())) continue;
                int drop = stack.getCount() - JUNK_DROP_THRESHOLD;
                serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                        entity.getX(), entity.getY(), entity.getZ(),
                        stack.copyWithCount(drop)));
                stack.shrink(drop);
            }
        }
    }

    private boolean isInventoryAlmostFull() {
        SimpleContainer inv = entity.getInventory();
        int occupied = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) occupied++;
        }
        return occupied >= inv.getContainerSize() - 4;
    }

    private boolean isJunk(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.COBBLESTONE
                || item == Items.COBBLED_DEEPSLATE
                || item == Items.GRAVEL
                || item == Items.DIRT
                || item == Items.SAND
                || item == Items.NETHERRACK;
    }
}
