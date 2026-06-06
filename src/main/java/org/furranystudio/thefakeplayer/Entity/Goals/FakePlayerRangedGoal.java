package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerRangedGoal extends Goal {

    private final FakePlayerEntity entity;
    private static final double MIN_DIST = 8.0;
    private static final double MAX_DIST = 15.0;
    private static final int DRAW_TIME = 20;
    private static final int POST_SHOT_COOLDOWN = 20;
    private int bowSlot = -1;
    private boolean movedBow = false;
    private Item movedBowItem = null;
    private int shootCooldown = 0;
    private boolean isDrawing = false;
    private int drawTicks = 0;

    public FakePlayerRangedGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = entity.getTarget();
        if (target == null) return false;
        if (entity.distanceTo(target) < MIN_DIST) return false;
        if (!hasArrows()) return false;
        return findAndSetBowSlot();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = entity.getTarget();
        if (target == null) return false;
        if (!hasBowInHand()) return false;
        if (movedBow && movedBowItem != null && entity.getMainHandItem().getItem() != movedBowItem) return false;
        return entity.distanceTo(target) > 3.0 && (hasArrows() || isDrawing);
    }

    @Override
    public void start() {
        movedBow = false;
        movedBowItem = null;
        shootCooldown = 0;
        isDrawing = false;
        drawTicks = 0;
        if (bowSlot >= 0) {
            ItemStack currentMain = entity.getMainHandItem();
            if (!currentMain.isEmpty()) {
                ItemStack leftover = entity.getInventory().addItem(currentMain.copy());
                if (!leftover.isEmpty() && entity.level() instanceof ServerLevel serverLevel) {
                    serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                            entity.getX(), entity.getY(), entity.getZ(), leftover));
                }
            }
            ItemStack bow = entity.getInventory().getItem(bowSlot).copy();
            entity.setItemInHand(InteractionHand.MAIN_HAND, bow);
            entity.getInventory().setItem(bowSlot, ItemStack.EMPTY);
            movedBowItem = bow.getItem();
            movedBow = true;
        }
    }

    @Override
    public void tick() {
        LivingEntity target = entity.getTarget();
        if (target == null) return;

        double dist = entity.distanceTo(target);
        boolean hasLOS = entity.getSensing().hasLineOfSight(target);

        entity.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Distance management
        if (dist < MIN_DIST) {
            Vec3 awayDir = entity.position().subtract(target.position()).normalize();
            entity.getNavigation().moveTo(
                    entity.getX() + awayDir.x * MIN_DIST,
                    entity.getY(),
                    entity.getZ() + awayDir.z * MIN_DIST,
                    1.0
            );
        } else if (dist > MAX_DIST || !hasLOS) {
            entity.getNavigation().moveTo(target, 1.0);
        } else {
            entity.getNavigation().stop();
        }

        // Shooting state machine
        if (isDrawing) {
            drawTicks++;
            if (drawTicks >= DRAW_TIME) {
                if (hasLOS) {
                    entity.performRangedAttack(target, 1.0f);
                }
                if (entity.isUsingItem()) entity.stopUsingItem();
                isDrawing = false;
                drawTicks = 0;
                shootCooldown = POST_SHOT_COOLDOWN;
            }
        } else if (shootCooldown > 0) {
            shootCooldown--;
        } else if (hasLOS && dist <= MAX_DIST && hasArrows()) {
            entity.startUsingItem(InteractionHand.MAIN_HAND);
            isDrawing = true;
            drawTicks = 0;
        }
    }

    @Override
    public void stop() {
        if (entity.isUsingItem()) entity.stopUsingItem();
        if (movedBow && movedBowItem != null) {
            ItemStack currentMain = entity.getMainHandItem();
            if (!currentMain.isEmpty() && currentMain.getItem() == movedBowItem) {
                if (bowSlot >= 0 && entity.getInventory().getItem(bowSlot).isEmpty()) {
                    entity.getInventory().setItem(bowSlot, currentMain.copy());
                } else {
                    entity.getInventory().addItem(currentMain.copy());
                }
                entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
        entity.getNavigation().stop();
        bowSlot = -1;
        movedBow = false;
        movedBowItem = null;
        shootCooldown = 0;
        isDrawing = false;
        drawTicks = 0;
    }

    private boolean findAndSetBowSlot() {
        Item mainHandItem = entity.getMainHandItem().getItem();
        if (mainHandItem instanceof BowItem || mainHandItem instanceof CrossbowItem) {
            bowSlot = -1;
            return true;
        }
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            Item item = entity.getInventory().getItem(i).getItem();
            if (item instanceof BowItem || item instanceof CrossbowItem) {
                bowSlot = i;
                return true;
            }
        }
        return false;
    }

    private boolean hasBowInHand() {
        Item item = entity.getMainHandItem().getItem();
        return item instanceof BowItem || item instanceof CrossbowItem;
    }

    private boolean hasArrows() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (entity.getInventory().getItem(i).getItem() instanceof ArrowItem) return true;
        }
        return false;
    }
}
