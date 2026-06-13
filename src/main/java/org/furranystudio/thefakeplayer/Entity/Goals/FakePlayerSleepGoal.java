package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerSleepGoal extends Goal {

    private final FakePlayerEntity entity;
    private BlockPos bedPos = null;
    private boolean sleeping = false;

    private static final int SEARCH_RANGE = 16;
    private static final double REACH_DISTANCE = 2.0;
    // Night: dayTime 13000–23000 out of 24000
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;
    // Interrupt sleep if hurt by a mob within this many ticks
    private static final long HURT_INTERRUPT_WINDOW = 100L;

    public FakePlayerSleepGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!isNight()) return false;
        if (entity.getTarget() != null) return false;
        if (entity.isSleeping()) return false;
        if (wasRecentlyHurt()) return false;
        bedPos = findFreeBed();
        return bedPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!isNight()) return false;
        if (entity.getTarget() != null) return false;
        if (wasRecentlyHurt()) return false;
        return bedPos != null;
    }

    @Override
    public void start() {
        sleeping = false;
        entity.getNavigation().moveTo(bedPos.getX() + 0.5, bedPos.getY(), bedPos.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        if (bedPos == null) return;

        // Wake up if hurt mid-sleep
        if (sleeping && wasRecentlyHurt()) {
            entity.stopSleeping();
            sleeping = false;
            return;
        }

        if (sleeping) {
            entity.getLookControl().setLookAt(
                    bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5);
            return;
        }

        double distSq = entity.distanceToSqr(bedPos.getX() + 0.5, bedPos.getY(), bedPos.getZ() + 0.5);
        if (distSq > REACH_DISTANCE * REACH_DISTANCE) {
            entity.getNavigation().moveTo(bedPos.getX() + 0.5, bedPos.getY(), bedPos.getZ() + 0.5, 1.0);
            return;
        }

        if (isBedFree(bedPos)) {
            entity.getNavigation().stop();
            entity.startSleeping(bedPos);
            sleeping = true;
            entity.sendContextualMessage(
                "thefakeplayer.chat.sleeping.0",
                "thefakeplayer.chat.sleeping.1",
                "thefakeplayer.chat.sleeping.2"
            );
        }
    }

    @Override
    public void stop() {
        if (entity.isSleeping()) {
            entity.stopSleeping();
            entity.sendContextualMessage(
                "thefakeplayer.chat.waking.0",
                "thefakeplayer.chat.waking.1",
                "thefakeplayer.chat.waking.2"
            );
        }
        sleeping = false;
        bedPos = null;
    }

    private boolean isNight() {
        long dayTime = entity.level().getDayTime() % 24000L;
        return dayTime >= NIGHT_START && dayTime <= NIGHT_END;
    }

    private boolean wasRecentlyHurt() {
        long gameTime = entity.level().getGameTime();
        return gameTime - entity.getLastHurtByMobTimestamp() < HURT_INTERRUPT_WINDOW;
    }

    private BlockPos findFreeBed() {
        BlockPos origin = entity.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RANGE, -3, -SEARCH_RANGE),
                origin.offset(SEARCH_RANGE, 3, SEARCH_RANGE))) {
            BlockState state = entity.level().getBlockState(pos);
            if (!state.is(BlockTags.BEDS)) continue;
            // Only target the head part to avoid duplicate matches for the same bed
            if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) != BedPart.HEAD) continue;
            if (isBedFree(pos)) return pos.immutable();
        }
        return null;
    }

    private boolean isBedFree(BlockPos pos) {
        BlockState state = entity.level().getBlockState(pos);
        if (!state.is(BlockTags.BEDS)) return false;
        if (!state.hasProperty(BedBlock.OCCUPIED)) return true;
        return !state.getValue(BedBlock.OCCUPIED);
    }
}
