package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
        // Flags vides : tourne librement sans bloquer ni être bloqué
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

        // Son de mastication toutes les 3 ticks
        if (eatTicks % 3 == 0) {
            entity.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F,
                    0.9F + entity.level().random.nextFloat() * 0.2F);
        }

        if (eatTicks >= EAT_DURATION) {
            var food = foodStack.get(DataComponents.FOOD);
            if (food != null) {
                ItemStack particleStack = foodStack.copy(); // save before shrink
                entity.heal(food.nutrition());
                foodStack.shrink(1);

                // Son de fin de repas (rot)
                entity.playSound(SoundEvents.PLAYER_BURP, 0.5F,
                        0.9F + entity.level().random.nextFloat() * 0.2F);

                // Particules d'item autour de la tête
                if (entity.level() instanceof ServerLevel serverLevel) {
                    var particleData = new ItemParticleOption(ParticleTypes.ITEM, particleStack);
                    for (int i = 0; i < 8; i++) {
                        double vx = (entity.level().random.nextFloat() - 0.5) * 0.3;
                        double vy = entity.level().random.nextFloat() * 0.2 + 0.1;
                        double vz = (entity.level().random.nextFloat() - 0.5) * 0.3;
                        serverLevel.sendParticles(particleData,
                                entity.getX(), entity.getY() + entity.getEyeHeight() - 0.2,
                                entity.getZ(), 1, vx, vy, vz, 0.0);
                    }
                }
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
