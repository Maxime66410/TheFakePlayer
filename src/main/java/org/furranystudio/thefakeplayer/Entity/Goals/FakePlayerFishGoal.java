package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerFishGoal extends Goal {

    private enum Phase { APPROACH, CAST, WAIT, REEL }

    private final FakePlayerEntity entity;
    private BlockPos waterTargetPos = null;
    private BlockPos edgePos = null;
    private Phase phase = Phase.APPROACH;
    private int fishWaitDuration = 0;
    private int fishWaitTick = 0;
    private int reelTick = 0;
    private int cooldown = 0;
    private int rodSlot = -1;
    private ItemStack savedMainHand = ItemStack.EMPTY;
    private boolean done = false;

    private static final int SEARCH_RANGE = 12;
    private static final double REACH_DISTANCE_SQ = 2.5 * 2.5;
    private static final int FISH_WAIT_MIN = 600;
    private static final int FISH_WAIT_MAX = 1200;
    private static final int REEL_DURATION = 30;
    private static final int COOLDOWN = 1200;

    public FakePlayerFishGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        if (entity.getTarget() != null) return false;
        rodSlot = findRodSlot();
        if (rodSlot < 0) return false;
        waterTargetPos = findWaterTarget();
        return waterTargetPos != null && edgePos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (done) return false;
        if (entity.getTarget() != null) return false;
        if (waterTargetPos == null) return false;
        return entity.level().getBlockState(waterTargetPos).is(Blocks.WATER);
    }

    @Override
    public void start() {
        phase = Phase.APPROACH;
        fishWaitTick = 0;
        reelTick = 0;
        done = false;
        fishWaitDuration = FISH_WAIT_MIN + entity.getRandom().nextInt(FISH_WAIT_MAX - FISH_WAIT_MIN);
        savedMainHand = entity.getMainHandItem().copy();
        ItemStack rod = entity.getInventory().getItem(rodSlot);
        entity.setItemInHand(InteractionHand.MAIN_HAND, rod);
        entity.getInventory().setItem(rodSlot, ItemStack.EMPTY);
        entity.getNavigation().moveTo(edgePos.getX() + 0.5, edgePos.getY(), edgePos.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        switch (phase) {
            case APPROACH -> tickApproach();
            case CAST    -> tickCast();
            case WAIT    -> tickWait();
            case REEL    -> tickReel();
        }
    }

    private void tickApproach() {
        entity.getLookControl().setLookAt(
                waterTargetPos.getX() + 0.5, waterTargetPos.getY() + 0.5, waterTargetPos.getZ() + 0.5);
        double distSq = entity.distanceToSqr(
                edgePos.getX() + 0.5, edgePos.getY(), edgePos.getZ() + 0.5);
        if (distSq <= REACH_DISTANCE_SQ) {
            entity.getNavigation().stop();
            phase = Phase.CAST;
        }
    }

    private void tickCast() {
        entity.getNavigation().stop();
        entity.startUsingItem(InteractionHand.MAIN_HAND);
        entity.setFishing(true); // setFishing = EntityData setter
        entity.setFishingTarget(waterTargetPos);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS,
                0.5f, 0.4f / (entity.getRandom().nextFloat() * 0.4f + 0.8f));
        phase = Phase.WAIT;
    }

    private void tickWait() {
        entity.getLookControl().setLookAt(
                waterTargetPos.getX() + 0.5, waterTargetPos.getY() + 0.5, waterTargetPos.getZ() + 0.5);
        fishWaitTick++;

        // Bubble trail starting 60 ticks before the catch
        if (fishWaitDuration - fishWaitTick <= 60 && fishWaitTick % 6 == 0) {
            if (entity.level() instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 3; i++) {
                    double ox = (entity.getRandom().nextDouble() - 0.5) * 1.5;
                    double oz = (entity.getRandom().nextDouble() - 0.5) * 1.5;
                    serverLevel.sendParticles(ParticleTypes.BUBBLE,
                            waterTargetPos.getX() + 0.5 + ox,
                            waterTargetPos.getY() + 0.1,
                            waterTargetPos.getZ() + 0.5 + oz,
                            1, -ox * 0.15, 0.05, -oz * 0.15, 0.0);
                }
            }
        }

        if (fishWaitTick >= fishWaitDuration) {
            reelTick = 0;
            phase = Phase.REEL;
        }
    }

    private void tickReel() {
        reelTick++;

        if (reelTick == 1) {
            entity.triggerSwingAnim();
            entity.stopUsingItem();
            entity.setFishing(false);

            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SPLASH,
                        waterTargetPos.getX() + 0.5, waterTargetPos.getY() + 0.2,
                        waterTargetPos.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.1);
            }
            entity.level().playSound(null,
                    waterTargetPos.getX() + 0.5, waterTargetPos.getY(), waterTargetPos.getZ() + 0.5,
                    SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS,
                    0.25f, 1.0f + (entity.getRandom().nextFloat() - entity.getRandom().nextFloat()) * 0.4f);
        }

        if (reelTick == 8) {
            ItemStack fish = new ItemStack(entity.getRandom().nextBoolean() ? Items.COD : Items.SALMON);
            entity.getInventory().addItem(fish);
        }

        if (reelTick >= REEL_DURATION) {
            done = true; // canContinueToUse → false → framework calls stop()
        }
    }

    @Override
    public void stop() {
        entity.setFishing(false);
        if (entity.isUsingItem()) entity.stopUsingItem();
        // Return rod to its original slot and restore what was in hand before
        ItemStack currentHand = entity.getMainHandItem();
        if (rodSlot >= 0 && currentHand.getItem() == Items.FISHING_ROD) {
            entity.getInventory().setItem(rodSlot, currentHand);
            entity.setItemInHand(InteractionHand.MAIN_HAND, savedMainHand.isEmpty() ? ItemStack.EMPTY : savedMainHand);
        }
        rodSlot = -1;
        waterTargetPos = null;
        edgePos = null;
        done = false;
        cooldown = COOLDOWN;
    }

    private int findRodSlot() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (entity.getInventory().getItem(i).getItem() == Items.FISHING_ROD) return i;
        }
        return -1;
    }

    private BlockPos findWaterTarget() {
        BlockPos origin = entity.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RANGE, -2, -SEARCH_RANGE),
                origin.offset(SEARCH_RANGE, 2, SEARCH_RANGE))) {
            if (!entity.level().getBlockState(pos).is(Blocks.WATER)) continue;
            if (!entity.level().getFluidState(pos).isSource()) continue;
            if (!entity.level().getBlockState(pos.above()).isAir()) continue;
            for (BlockPos adj : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west()}) {
                // Case 1: adj at water level is air, with solid non-water ground below (e.g. elevated water)
                BlockPos stand = validStandPos(adj);
                if (stand == null) {
                    // Case 2: adj at water level is solid (common bank) — player stands on top
                    stand = validStandPos(adj.above());
                }
                if (stand != null) {
                    edgePos = stand;
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    // Returns pos if the entity can stand there (air at feet, solid non-water below), else null
    private BlockPos validStandPos(BlockPos pos) {
        if (!entity.level().getBlockState(pos).isAir()) return null;
        BlockState below = entity.level().getBlockState(pos.below());
        if (below.isAir()) return null;
        if (!below.getFluidState().isEmpty()) return null;
        return pos.immutable();
    }
}
