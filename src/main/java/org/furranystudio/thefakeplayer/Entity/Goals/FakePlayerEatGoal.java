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
    private ItemStack savedMainHand = ItemStack.EMPTY;
    private int eatTicks = 0;
    private static final int EAT_DURATION = 20; // ~1s comme un vrai joueur

    public FakePlayerEatGoal(FakePlayerEntity entity) {
        this.entity = entity;
        // Flags vides : tourne librement sans bloquer ni être bloqué
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        // En combat : mange uniquement si vie ≤ 20%
        if (entity.getTarget() != null && health > maxHealth * 0.2f) return false;
        if (health >= maxHealth) return false;
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
        entity.setEatAnimTick(0);
        savedMainHand = entity.getMainHandItem().copy();
        entity.setItemInHand(InteractionHand.MAIN_HAND, foodStack.copy());
    }

    @Override
    public void tick() {
        eatTicks++;
        entity.setEatAnimTick(eatTicks); // synchronisé vers clients via EntityData

        // Son de mastication + particules toutes les 4 ticks pendant l'animation
        if (eatTicks % 4 == 0) {
            entity.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F,
                    0.9F + entity.level().random.nextFloat() * 0.2F);

            // Particules pendant la mastication (serveur uniquement)
            if (!foodStack.isEmpty() && entity.level() instanceof ServerLevel serverLevel) {
                var particleData = new ItemParticleOption(ParticleTypes.ITEM, foodStack);
                double vx = (entity.level().random.nextFloat() - 0.5) * 0.2;
                double vy = entity.level().random.nextFloat() * 0.1 + 0.05;
                double vz = (entity.level().random.nextFloat() - 0.5) * 0.2;
                serverLevel.sendParticles(particleData,
                        entity.getX(), entity.getY() + entity.getEyeHeight() - 0.3,
                        entity.getZ(), 1, vx, vy, vz, 0.0);
            }
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
        entity.setEatAnimTick(0);
        foodStack = ItemStack.EMPTY;
        entity.setItemInHand(InteractionHand.MAIN_HAND, savedMainHand);
        savedMainHand = ItemStack.EMPTY;
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
