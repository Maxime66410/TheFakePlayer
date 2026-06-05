package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerFleeGoal extends Goal {

    private final FakePlayerEntity entity;
    private static final float FLEE_HEALTH_RATIO = 0.2f;
    private static final double FLEE_SPEED = 1.5;
    private static final double FLEE_RANGE = 10.0;

    public FakePlayerFleeGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return entity.getTarget() != null
                && entity.getHealth() < entity.getMaxHealth() * FLEE_HEALTH_RATIO
                && !entity.hasFood()
                && !entity.hasHealingPotion();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getTarget() != null
                && entity.getHealth() < entity.getMaxHealth() * FLEE_HEALTH_RATIO
                && !entity.hasFood()
                && !entity.hasHealingPotion();
    }

    @Override
    public void tick() {
        LivingEntity target = entity.getTarget();
        if (target == null) return;
        Vec3 awayDir = entity.position().subtract(target.position()).normalize();
        entity.getNavigation().moveTo(
                entity.getX() + awayDir.x * FLEE_RANGE,
                entity.getY(),
                entity.getZ() + awayDir.z * FLEE_RANGE,
                FLEE_SPEED
        );
        entity.setTarget(null);
    }

    @Override
    public void stop() {
        entity.suppressTargetingTicks = 100;
    }
}
