package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

import static net.minecraft.world.item.Items.*;

public class FakePlayerSmithGoal extends Goal {

    private final FakePlayerEntity entity;
    private int cooldown = 0;
    private int smithTick = 0;
    private boolean done = false;

    private BlockPos tablePos = null;
    private UpgradeRecipe pendingUpgrade = null;

    private static final int SEARCH_RANGE = 50;
    private static final int SMITH_DURATION = 60;
    private static final int COOLDOWN = 400;

    // base → netherite result; armorSlot = null for tools/weapons
    record UpgradeRecipe(Item base, Item result, EquipmentSlot armorSlot) {}

    private static final UpgradeRecipe[] UPGRADES = {
        new UpgradeRecipe(DIAMOND_SWORD,      NETHERITE_SWORD,      null),
        new UpgradeRecipe(DIAMOND_PICKAXE,    NETHERITE_PICKAXE,    null),
        new UpgradeRecipe(DIAMOND_AXE,        NETHERITE_AXE,        null),
        new UpgradeRecipe(DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE, EquipmentSlot.CHEST),
        new UpgradeRecipe(DIAMOND_LEGGINGS,   NETHERITE_LEGGINGS,   EquipmentSlot.LEGS),
        new UpgradeRecipe(DIAMOND_HELMET,     NETHERITE_HELMET,     EquipmentSlot.HEAD),
        new UpgradeRecipe(DIAMOND_BOOTS,      NETHERITE_BOOTS,      EquipmentSlot.FEET),
    };

    public FakePlayerSmithGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    // ── Goal lifecycle ────────────────────────────────────────────────────────

    @Override
    public boolean canUse() {
        if (entity.level().isClientSide()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        if (!hasItem(NETHERITE_INGOT)) return false;
        if (!hasItem(NETHERITE_UPGRADE_SMITHING_TEMPLATE)) return false;

        pendingUpgrade = null;
        for (UpgradeRecipe upgrade : UPGRADES) {
            if (!hasItemAnywhere(upgrade.base())) continue;
            if (hasItemAnywhere(upgrade.result())) continue;
            pendingUpgrade = upgrade;
            break;
        }
        if (pendingUpgrade == null) return false;

        tablePos = findSmithingTable();
        return tablePos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !done;
    }

    @Override
    public void start() {
        done = false;
        smithTick = 0;
        entity.getNavigation().moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        if (cooldown == 0) cooldown = COOLDOWN;
        tablePos = null;
        pendingUpgrade = null;
        done = false;
        smithTick = 0;
    }

    @Override
    public void tick() {
        if (tablePos == null || !entity.level().getBlockState(tablePos).is(Blocks.SMITHING_TABLE)) {
            done = true;
            return;
        }

        entity.getLookControl().setLookAt(tablePos.getX() + 0.5, tablePos.getY() + 0.5, tablePos.getZ() + 0.5);

        if (entity.blockPosition().distSqr(tablePos) > 6.25) {
            entity.getNavigation().moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 1.0);
            return;
        }

        entity.getNavigation().stop();
        smithTick++;
        if (smithTick == 1) entity.triggerSwingAnim();

        if (smithTick >= SMITH_DURATION) {
            applyUpgrade();
            done = true;
        }
    }

    // ── Upgrade logic ─────────────────────────────────────────────────────────

    private void applyUpgrade() {
        if (pendingUpgrade == null) return;
        consumeItem(NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        consumeItem(NETHERITE_INGOT);
        consumeItemAnywhere(pendingUpgrade.base());
        ItemStack result = new ItemStack(pendingUpgrade.result());
        if (pendingUpgrade.armorSlot() != null) {
            entity.setItemSlot(pendingUpgrade.armorSlot(), result);
        } else {
            addToInventory(result);
        }
        entity.triggerSwingAnim();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BlockPos findSmithingTable() {
        BlockPos origin = entity.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!entity.level().getBlockState(pos).is(Blocks.SMITHING_TABLE)) continue;
                    double dist = origin.distSqr(pos);
                    if (dist < bestDist) { bestDist = dist; best = pos.immutable(); }
                }
            }
        }
        return best;
    }

    private boolean hasItem(Item item) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) return true;
        }
        return false;
    }

    private boolean hasItemAnywhere(Item item) {
        if (hasItem(item)) return true;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (entity.getItemBySlot(slot).getItem() == item) return true;
        }
        return false;
    }

    private void consumeItem(Item item) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                stack.shrink(1);
                return;
            }
        }
    }

    // Consumes from inventory first, then equipment slots (base diamond item may be equipped)
    private void consumeItemAnywhere(Item item) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                stack.shrink(1);
                return;
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                entity.setItemSlot(slot, ItemStack.EMPTY);
                return;
            }
        }
    }

    private void addToInventory(ItemStack stack) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack slot = entity.getInventory().getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItem(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                int add = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(add);
                stack.shrink(add);
                if (stack.isEmpty()) return;
            }
        }
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (entity.getInventory().getItem(i).isEmpty()) {
                entity.getInventory().setItem(i, stack.copy());
                return;
            }
        }
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            entity.spawnAtLocation(serverLevel, stack);
        }
    }
}
