package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerEatGoal extends Goal {

    private final FakePlayerEntity entity;
    private ItemStack foodStack = ItemStack.EMPTY;
    private int eatTicks = 0;
    private static final int EAT_DURATION = 32; // ~1.6s comme un vrai joueur

    public FakePlayerEatGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (entity.getHealth() >= entity.getMaxHealth()) return false;
        foodStack = findFood();
        return !foodStack.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return eatTicks < EAT_DURATION;
    }

    @Override
    public void start() {
        eatTicks = 0;
    }

    @Override
    public void tick() {
        eatTicks++;
        if (eatTicks >= EAT_DURATION) {
            var food = foodStack.get(DataComponents.FOOD);
            if (food != null) {
                entity.heal(food.nutrition());
                foodStack.shrink(1);
            }
        }
    }

    @Override
    public void stop() {
        eatTicks = 0;
        foodStack = ItemStack.EMPTY;
    }

    private ItemStack findFood() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
