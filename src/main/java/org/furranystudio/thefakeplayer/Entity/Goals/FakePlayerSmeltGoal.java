package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerSmeltGoal extends Goal {

    private static final int SEARCH_RANGE = 8;
    private static final int COOLDOWN_TICKS = 400;

    private final FakePlayerEntity entity;
    private BlockPos furnacePos = null;
    private int cooldown = 0;

    public FakePlayerSmeltGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        if (entity.getTarget() != null) return false;
        furnacePos = findFurnace();
        if (furnacePos == null) return false;
        AbstractFurnaceBlockEntity be = getFurnaceBE(furnacePos);
        if (be == null) return false;
        // Collect mode: output is waiting
        if (!be.getItem(2).isEmpty()) return true;
        // Insert mode: have smeltable items and fuel (in inventory or already in furnace)
        return hasSmeltableItems() && (hasFuelInInventory() || !be.getItem(1).isEmpty());
    }

    @Override
    public boolean canContinueToUse() {
        return furnacePos != null && isFurnace(entity.level().getBlockState(furnacePos));
    }

    @Override
    public void start() {
        entity.getNavigation().moveTo(furnacePos.getX() + 0.5, furnacePos.getY(), furnacePos.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        if (furnacePos == null) return;
        entity.getLookControl().setLookAt(furnacePos.getX() + 0.5, furnacePos.getY() + 0.5, furnacePos.getZ() + 0.5);

        if (entity.blockPosition().distSqr(furnacePos) > 9.0) {
            entity.getNavigation().moveTo(furnacePos.getX() + 0.5, furnacePos.getY(), furnacePos.getZ() + 0.5, 1.0);
            return;
        }

        entity.getNavigation().stop();
        AbstractFurnaceBlockEntity be = getFurnaceBE(furnacePos);
        if (be != null) interactWithFurnace(be);
        cooldown = COOLDOWN_TICKS;
        furnacePos = null;
    }

    @Override
    public void stop() {
        furnacePos = null;
        if (cooldown == 0) cooldown = COOLDOWN_TICKS;
        entity.getNavigation().stop();
    }

    private void interactWithFurnace(AbstractFurnaceBlockEntity be) {
        entity.triggerSwingAnim();

        // Priority 1: collect output
        ItemStack output = be.getItem(2);
        if (!output.isEmpty()) {
            entity.getInventory().addItem(output.copy());
            be.removeItem(2, output.getCount());
            return;
        }

        // Priority 2: add fuel if slot is empty
        if (be.getItem(1).isEmpty()) {
            int fuelSlot = findFuelSlot();
            if (fuelSlot >= 0) {
                be.setItem(1, entity.getInventory().getItem(fuelSlot).split(1));
            }
        }

        // Priority 3: add ore if input slot is empty (one coal smelts 8 items)
        if (be.getItem(0).isEmpty()) {
            int oreSlot = findSmeltableSlot();
            if (oreSlot >= 0) {
                ItemStack ore = entity.getInventory().getItem(oreSlot);
                be.setItem(0, ore.split(Math.min(ore.getCount(), 8)));
                entity.sendContextualMessage(
                    "thefakeplayer.chat.smelting.0",
                    "thefakeplayer.chat.smelting.1"
                );
            }
        }
    }

    private boolean hasSmeltableItems() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (isSmeltable(entity.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private boolean hasFuelInInventory() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (isFuel(entity.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private int findSmeltableSlot() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (isSmeltable(entity.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private int findFuelSlot() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (isFuel(stack) && !isSmeltable(stack)) return i;
        }
        return -1;
    }

    private boolean isSmeltable(ItemStack stack) {
        if (stack.isEmpty() || !(entity.level() instanceof ServerLevel sl)) return false;
        return sl.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), sl)
                .isPresent();
    }

    private boolean isFuel(ItemStack stack) {
        if (stack.isEmpty()) return false;
        FuelValues fuelValues = FuelValues.vanillaBurnTimes(entity.level().registryAccess(), entity.level().enabledFeatures());
        return fuelValues.isFuel(stack);
    }

    private AbstractFurnaceBlockEntity getFurnaceBE(BlockPos pos) {
        if (!(entity.level() instanceof ServerLevel sl)) return null;
        BlockEntity be = sl.getBlockEntity(pos);
        return be instanceof AbstractFurnaceBlockEntity furnaceBE ? furnaceBE : null;
    }

    private BlockPos findFurnace() {
        BlockPos origin = entity.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (isFurnace(entity.level().getBlockState(pos))) {
                        double dist = origin.distSqr(pos);
                        if (dist < bestDist) { bestDist = dist; best = pos.immutable(); }
                    }
                }
            }
        }
        return best;
    }

    private boolean isFurnace(BlockState state) {
        return state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER);
    }
}
