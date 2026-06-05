package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerLongDistanceTravelGoal extends Goal {

    private final FakePlayerEntity entity;
    private static final double MIN_DIST_SQ = 20.0 * 20.0;
    private static final double SPRINT_JUMP_BOOST = 0.2;
    private static final int JUMP_COOLDOWN = 4;
    private boolean wasOnGround = true;
    private int jumpCooldown = 0;

    public FakePlayerLongDistanceTravelGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return entity.getTarget() == null
                && !entity.getNavigation().isDone()
                && isPathLong();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getTarget() == null
                && !entity.getNavigation().isDone()
                && isPathLong();
    }

    @Override
    public void start() {
        wasOnGround = false;
        jumpCooldown = 0;
    }

    @Override
    public void tick() {
        entity.setSprinting(true);

        boolean onGround = entity.onGround() && !entity.isInWater();

        if (jumpCooldown > 0) jumpCooldown--;

        if (onGround && !wasOnGround && jumpCooldown <= 0) {
            entity.getJumpControl().jump();
            applyWaypointBoost();
            jumpCooldown = JUMP_COOLDOWN;
        }

        wasOnGround = onGround;
    }

    @Override
    public void stop() {
        entity.setSprinting(false);
        wasOnGround = true;
        jumpCooldown = 0;
    }

    private void applyWaypointBoost() {
        Path path = entity.getNavigation().getPath();
        if (path == null) return;
        int nextIdx = path.getNextNodeIndex();
        if (nextIdx >= path.getNodeCount()) return;
        Node node = path.getNode(nextIdx);
        double dx = (node.x + 0.5) - entity.getX();
        double dz = (node.z + 0.5) - entity.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return;
        Vec3 vel = entity.getDeltaMovement();
        entity.setDeltaMovement(
                vel.x + (dx / len) * SPRINT_JUMP_BOOST,
                vel.y,
                vel.z + (dz / len) * SPRINT_JUMP_BOOST
        );
    }

    private boolean isPathLong() {
        Path path = entity.getNavigation().getPath();
        if (path == null) return false;
        BlockPos target = path.getTarget();
        if (target == null) return false;
        return entity.blockPosition().distSqr(target) > MIN_DIST_SQ;
    }
}
