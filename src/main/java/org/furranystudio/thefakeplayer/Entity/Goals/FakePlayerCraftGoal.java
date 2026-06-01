package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerCraftGoal extends Goal {

    private final FakePlayerEntity entity;
    private BlockPos tablePos = null;
    private int cooldown = 0;
    private int craftTick = 0;
    private int craftDuration = 0;

    private static final int SEARCH_RANGE = 8;
    private static final int COOLDOWN = 250;

    public FakePlayerCraftGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        tablePos = findCraftingTable();
        return tablePos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return tablePos != null && entity.level().getBlockState(tablePos).is(Blocks.CRAFTING_TABLE);
    }

    @Override
    public void start() {
        craftTick = 0;
        craftDuration = 60 + entity.level().random.nextInt(41); // 60-100 ticks
        entity.getNavigation().moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        if (tablePos == null) return;

        entity.getLookControl().setLookAt(tablePos.getX() + 0.5, tablePos.getY() + 0.5, tablePos.getZ() + 0.5);

        if (entity.blockPosition().distSqr(tablePos) > 6.25) {
            entity.getNavigation().moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 1.0);
            return;
        }

        entity.getNavigation().stop();
        craftTick++;

        if (craftTick == 1) {
            entity.triggerSwingAnim();
        }

        if (craftTick >= craftDuration) {
            cooldown = COOLDOWN;
            tablePos = null;
        }
    }

    @Override
    public void stop() {
        if (cooldown == 0) cooldown = COOLDOWN;
        tablePos = null;
        craftTick = 0;
        entity.getNavigation().stop();
    }

    private BlockPos findCraftingTable() {
        BlockPos origin = entity.blockPosition();
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!entity.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)) continue;
                    double dist = origin.distSqr(pos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestPos = pos.immutable();
                    }
                }
            }
        }
        return bestPos;
    }
}
