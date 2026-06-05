package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerWeaponSelectGoal extends Goal {

    private enum TargetContext { UNDEAD, ARTHROPOD, CREEPER, DEFAULT }

    private final FakePlayerEntity entity;
    private int weaponSlot = -1;
    private int shieldSlot = -1;
    private boolean movedWeapon = false;
    private boolean movedShield = false;
    private TargetContext currentContext = TargetContext.DEFAULT;

    private static final float CRITICAL_HEALTH_RATIO = 0.2f;
    private static final double SHIELD_RAISE_DISTANCE = 3.5;
    private static final double SPRINT_DISTANCE = 8.0;

    public FakePlayerWeaponSelectGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (entity.getTarget() == null) return false;
        if (entity.getHealth() <= entity.getMaxHealth() * CRITICAL_HEALTH_RATIO) return false;

        currentContext = getContext(entity.getTarget());
        weaponSlot = findBestWeaponSlot(currentContext);
        shieldSlot = findShieldSlot();

        boolean hasWeapon = weaponSlot >= 0 || getWeaponScore(entity.getMainHandItem(), currentContext) > 0;
        boolean hasShield = shieldSlot >= 0 || entity.getOffhandItem().getItem() instanceof ShieldItem;

        return hasWeapon || hasShield;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.getTarget() == null) return false;
        if (entity.getHealth() <= entity.getMaxHealth() * CRITICAL_HEALTH_RATIO) return false;
        return true;
    }

    @Override
    public void start() {
        movedWeapon = false;
        movedShield = false;

        ItemStack currentMain = entity.getMainHandItem();

        if (weaponSlot >= 0) {
            ItemStack candidate = entity.getInventory().getItem(weaponSlot);
            if (getWeaponScore(candidate, currentContext) > getWeaponScore(currentMain, currentContext)) {
                if (!currentMain.isEmpty()) {
                    entity.getInventory().addItem(currentMain.copy());
                }
                entity.setItemInHand(InteractionHand.MAIN_HAND, candidate.copy());
                entity.getInventory().setItem(weaponSlot, ItemStack.EMPTY);
                movedWeapon = true;
            } else {
                weaponSlot = -1;
            }
        }

        if (shieldSlot >= 0 && !(entity.getOffhandItem().getItem() instanceof ShieldItem)) {
            ItemStack currentOff = entity.getOffhandItem();
            ItemStack shield = entity.getInventory().getItem(shieldSlot);
            if (!currentOff.isEmpty()) {
                entity.getInventory().addItem(currentOff.copy());
            }
            entity.setItemInHand(InteractionHand.OFF_HAND, shield.copy());
            entity.getInventory().setItem(shieldSlot, ItemStack.EMPTY);
            movedShield = true;
        } else {
            shieldSlot = -1;
        }
    }

    @Override
    public void tick() {
        LivingEntity target = entity.getTarget();
        if (target == null) return;

        currentContext = getContext(target);
        double dist = entity.distanceTo(target);
        boolean hasShield = entity.getOffhandItem().getItem() instanceof ShieldItem;

        entity.setSprinting(dist > 2.5 && dist <= SPRINT_DISTANCE);

        if (dist <= SHIELD_RAISE_DISTANCE && hasShield && entity.shieldCooldown <= 0) {
            if (!entity.isUsingItem()) {
                entity.startUsingItem(InteractionHand.OFF_HAND);
            }
        } else if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof ShieldItem) {
            entity.stopUsingItem();
        }
    }

    @Override
    public void stop() {
        entity.setSprinting(false);
        entity.stopUsingItem();

        if (movedWeapon && weaponSlot >= 0) {
            returnToInventory(InteractionHand.MAIN_HAND, weaponSlot);
        }
        if (movedShield && shieldSlot >= 0) {
            returnToInventory(InteractionHand.OFF_HAND, shieldSlot);
        }

        weaponSlot = -1;
        shieldSlot = -1;
        movedWeapon = false;
        movedShield = false;
        currentContext = TargetContext.DEFAULT;
    }

    private void returnToInventory(InteractionHand hand, int slot) {
        ItemStack handItem = entity.getItemInHand(hand);
        if (handItem.isEmpty()) return;
        if (entity.getInventory().getItem(slot).isEmpty()) {
            entity.getInventory().setItem(slot, handItem.copy());
        } else {
            entity.getInventory().addItem(handItem.copy());
        }
        entity.setItemInHand(hand, ItemStack.EMPTY);
    }

    private TargetContext getContext(LivingEntity target) {
        if (target instanceof Creeper) return TargetContext.CREEPER;
        if (target.getType().is(EntityTypeTags.SENSITIVE_TO_SMITE)) return TargetContext.UNDEAD;
        if (target.getType().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) return TargetContext.ARTHROPOD;
        return TargetContext.DEFAULT;
    }

    private int findBestWeaponSlot(TargetContext context) {
        int bestSlot = -1;
        float bestScore = getWeaponScore(entity.getMainHandItem(), context);
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            float score = getWeaponScore(entity.getInventory().getItem(i), context);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int findShieldSlot() {
        if (entity.getOffhandItem().getItem() instanceof ShieldItem) return -1;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (entity.getInventory().getItem(i).getItem() instanceof ShieldItem) return i;
        }
        return -1;
    }

    private float getWeaponScore(ItemStack stack, TargetContext context) {
        if (stack.isEmpty()) return 0f;
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        float total = 0f;
        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            if (entry.attribute().value() == Attributes.ATTACK_DAMAGE.value()) {
                total += (float) entry.modifier().amount();
            }
        }
        if (total == 0f) return 0f;
        switch (context) {
            case UNDEAD -> total += getEnchantmentLevel(stack, Enchantments.SMITE) * 2.5f;
            case ARTHROPOD -> total += getEnchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS) * 2.5f;
            default -> {}
        }
        return total;
    }

    private int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(key)) return entry.getIntValue();
        }
        return 0;
    }
}
