package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerPotionGoal extends Goal {

    private final FakePlayerEntity entity;
    private int potionSlot = -1;
    private Item potionItemInHand = null;
    private boolean isThrowable = false;
    private static final float HEAL_HEALTH_RATIO = 0.5f;

    public FakePlayerPotionGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (entity.isUsingItem()) return false;
        boolean needsHeal = entity.getHealth() < entity.getMaxHealth() * HEAL_HEALTH_RATIO;
        if (needsHeal) {
            potionSlot = findPotionSlot(true);
            if (potionSlot >= 0) {
                isThrowable = isThrowablePotion(entity.getInventory().getItem(potionSlot));
                return true;
            }
        }
        if (entity.getTarget() != null && !entity.hasEffect(MobEffects.DAMAGE_BOOST)) {
            potionSlot = findPotionSlot(false);
            if (potionSlot >= 0) {
                isThrowable = isThrowablePotion(entity.getInventory().getItem(potionSlot));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (isThrowable) return false;
        return entity.isUsingItem()
                && potionItemInHand != null
                && entity.getMainHandItem().getItem() == potionItemInHand;
    }

    @Override
    public void start() {
        ItemStack potion = entity.getInventory().getItem(potionSlot).copy();
        entity.getInventory().setItem(potionSlot, ItemStack.EMPTY);

        if (isThrowable) {
            if (!(entity.level() instanceof ServerLevel serverLevel)) return;
            ThrownPotion projectile = new ThrownPotion(serverLevel, entity, potion);
            projectile.setPos(entity.getX(), entity.getEyeY() - 0.1, entity.getZ());
            projectile.shoot(0, -1, 0, 0.5f, 0);
            serverLevel.addFreshEntity(projectile);
        } else {
            ItemStack currentMain = entity.getMainHandItem();
            if (!currentMain.isEmpty()) {
                ItemStack leftover = entity.getInventory().addItem(currentMain.copy());
                if (!leftover.isEmpty() && entity.level() instanceof ServerLevel serverLevel) {
                    serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                            entity.getX(), entity.getY(), entity.getZ(), leftover));
                }
            }
            potionItemInHand = potion.getItem();
            entity.setItemInHand(InteractionHand.MAIN_HAND, potion);
            entity.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    @Override
    public void tick() {
        // Vanilla LivingEntity handles the use duration and effect application automatically
    }

    @Override
    public void stop() {
        if (!isThrowable) {
            if (entity.isUsingItem()) entity.stopUsingItem();
            ItemStack currentMain = entity.getMainHandItem();
            if (!currentMain.isEmpty() && potionItemInHand != null && currentMain.getItem() == potionItemInHand) {
                ItemStack leftover = entity.getInventory().addItem(currentMain.copy());
                if (!leftover.isEmpty() && entity.level() instanceof ServerLevel serverLevel) {
                    serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                            entity.getX(), entity.getY(), entity.getZ(), leftover));
                }
                entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
        potionSlot = -1;
        potionItemInHand = null;
        isThrowable = false;
    }

    private int findPotionSlot(boolean healing) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (!isDrinkablePotion(stack) && !isThrowablePotion(stack)) continue;
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) continue;
            for (MobEffectInstance eff : contents.getAllEffects()) {
                if (healing && (eff.getEffect() == MobEffects.HEAL || eff.getEffect() == MobEffects.REGENERATION)) return i;
                if (!healing && eff.getEffect() == MobEffects.DAMAGE_BOOST) return i;
            }
        }
        return -1;
    }

    private boolean isDrinkablePotion(ItemStack stack) {
        return stack.getItem() instanceof PotionItem
                && !(stack.getItem() instanceof SplashPotionItem)
                && !(stack.getItem() instanceof LingeringPotionItem);
    }

    private boolean isThrowablePotion(ItemStack stack) {
        return stack.getItem() instanceof SplashPotionItem
                || stack.getItem() instanceof LingeringPotionItem;
    }
}
