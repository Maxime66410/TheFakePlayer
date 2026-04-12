package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
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
        // Flags vides : le goal tourne librement sans bloquer ni être bloqué par d'autres goals
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (entity.getHealth() >= entity.getMaxHealth()) return false;
        foodStack = findFood();
        return !foodStack.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return eatTicks < EAT_DURATION && entity.getHealth() < entity.getMaxHealth();
    }

    @Override
    public void start() {
        eatTicks = 0;
        entity.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public void tick() {
        eatTicks++;

        // Son de repas toutes les 4 ticks pendant la mangée
        if (eatTicks % 4 == 0) {
            entity.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F,
                    0.9F + entity.level().random.nextFloat() * 0.2F);
        }

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
