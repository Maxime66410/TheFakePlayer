package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerCreeperFleeGoal extends Goal {

    private final FakePlayerEntity entity;
    private static final double SHIELD_DIST = 3.0;
    private static final double FLEE_SPEED = 1.5;
    private static final double FLEE_RANGE = 8.0;

    public FakePlayerCreeperFleeGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = entity.getTarget();
        return target instanceof Creeper creeper
                && creeper.getSwellDir() > 0
                && entity.getHealth() > entity.getMaxHealth() * 0.2f;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = entity.getTarget();
        return target instanceof Creeper creeper
                && creeper.getSwellDir() > 0
                && entity.getHealth() > entity.getMaxHealth() * 0.2f;
    }

    @Override
    public void tick() {
        Creeper creeper = (Creeper) entity.getTarget();
        if (creeper == null) return;

        double dist = entity.distanceTo(creeper);
        boolean hasShield = entity.getOffhandItem().getItem() instanceof ShieldItem;

        if (hasShield) {
            // Has shield — hold position and block
            if (entity.shieldCooldown <= 0 && !entity.isUsingItem()) {
                entity.startUsingItem(InteractionHand.OFF_HAND);
            }
        } else {
            // No shield — flee
            if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof ShieldItem) {
                entity.stopUsingItem();
            }
            Vec3 awayDir = entity.position().subtract(creeper.position()).normalize();
            entity.getNavigation().moveTo(
                    entity.getX() + awayDir.x * FLEE_RANGE,
                    entity.getY(),
                    entity.getZ() + awayDir.z * FLEE_RANGE,
                    FLEE_SPEED
            );
        }
    }

    @Override
    public void stop() {
        if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof ShieldItem) {
            entity.stopUsingItem();
        }
    }
}
