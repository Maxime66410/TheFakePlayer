package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerAnvilRepairGoal extends Goal {

    private static final int SEARCH_RANGE = 8;
    private static final int REPAIR_TICKS = 60;
    private static final int COOLDOWN_TICKS = 600;
    private static final int DAMAGE_THRESHOLD = 25; // repair only if >25% damaged

    private final FakePlayerEntity entity;
    private BlockPos anvilPos = null;
    private int repairTick = 0;
    private int cooldown = 0;

    public FakePlayerAnvilRepairGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        ItemStack damaged = findMostDamagedRepairable();
        if (damaged == null) return false;
        if (findRepairMaterialSlot(damaged) < 0) return false;
        anvilPos = findAnvil();
        return anvilPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (anvilPos == null) return false;
        return isAnvil(entity.level().getBlockState(anvilPos));
    }

    @Override
    public void start() {
        repairTick = 0;
        entity.getNavigation().moveTo(anvilPos.getX() + 0.5, anvilPos.getY(), anvilPos.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        if (anvilPos == null) return;
        entity.getLookControl().setLookAt(anvilPos.getX() + 0.5, anvilPos.getY() + 0.5, anvilPos.getZ() + 0.5);

        if (entity.blockPosition().distSqr(anvilPos) > 9.0) {
            entity.getNavigation().moveTo(anvilPos.getX() + 0.5, anvilPos.getY(), anvilPos.getZ() + 0.5, 1.0);
            return;
        }

        entity.getNavigation().stop();
        repairTick++;

        if (repairTick == 1) {
            entity.triggerSwingAnim();
        }

        if (repairTick >= REPAIR_TICKS) {
            doRepair();
            cooldown = COOLDOWN_TICKS;
            anvilPos = null;
        }
    }

    @Override
    public void stop() {
        anvilPos = null;
        repairTick = 0;
        if (cooldown == 0) cooldown = COOLDOWN_TICKS;
        entity.getNavigation().stop();
    }

    private void doRepair() {
        ItemStack damaged = findMostDamagedRepairable();
        if (damaged == null) return;
        int matSlot = findRepairMaterialSlot(damaged);
        if (matSlot < 0) return;

        // Restore 25% of max durability per material consumed (vanilla approximation)
        int repairAmount = damaged.getMaxDamage() / 4;
        damaged.setDamageValue(Math.max(0, damaged.getDamageValue() - repairAmount));
        entity.getInventory().getItem(matSlot).shrink(1);

        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        entity.sendContextualMessage(
            "thefakeplayer.chat.repairing.0",
            "thefakeplayer.chat.repairing.1"
        );
    }

    // Returns the most-damaged repairable item (inventory + armor slots), null if nothing needs repair
    private ItemStack findMostDamagedRepairable() {
        ItemStack worst = null;
        int worstRatio = 0;

        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (isRepairable(stack)) {
                int ratio = stack.getDamageValue() * 100 / stack.getMaxDamage();
                if (ratio > worstRatio) { worstRatio = ratio; worst = stack; }
            }
        }

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (isRepairable(stack)) {
                int ratio = stack.getDamageValue() * 100 / stack.getMaxDamage();
                if (ratio > worstRatio) { worstRatio = ratio; worst = stack; }
            }
        }

        return worstRatio >= DAMAGE_THRESHOLD ? worst : null;
    }

    private boolean isRepairable(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamaged() && stack.has(DataComponents.REPAIRABLE);
    }

    // Returns inventory slot of a matching repair material, or -1
    private int findRepairMaterialSlot(ItemStack damaged) {
        Repairable repairable = damaged.get(DataComponents.REPAIRABLE);
        if (repairable == null) return -1;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack candidate = entity.getInventory().getItem(i);
            if (!candidate.isEmpty() && repairable.isValidRepairItem(candidate)) return i;
        }
        return -1;
    }

    private BlockPos findAnvil() {
        BlockPos origin = entity.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (isAnvil(entity.level().getBlockState(pos))) {
                        double dist = origin.distSqr(pos);
                        if (dist < bestDist) { bestDist = dist; best = pos.immutable(); }
                    }
                }
            }
        }
        return best;
    }

    private boolean isAnvil(BlockState state) {
        return state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL);
    }
}
