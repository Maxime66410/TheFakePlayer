package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerHarvestGoal extends Goal {

    private final FakePlayerEntity entity;
    private BlockPos targetCrop = null;
    private int harvestTick = 0;
    private int cooldown = 0;
    private static final int HARVEST_DELAY = 60;   // 3s de cassage
    private static final int SEARCH_RANGE = 8;
    private static final int COOLDOWN_TICKS = 100; // 5s si aucune culture trouvée

    public FakePlayerHarvestGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        targetCrop = findMatureCrop();
        return targetCrop != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetCrop == null) return false;
        BlockState state = entity.level().getBlockState(targetCrop);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    @Override
    public void start() {
        harvestTick = 0;
        entity.getNavigation().moveTo(
                targetCrop.getX() + 0.5, targetCrop.getY(), targetCrop.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        if (targetCrop == null) return;

        // Se repositionner si encore loin de la culture
        if (entity.blockPosition().distSqr(targetCrop) > 4.0) {
            entity.getNavigation().moveTo(
                    targetCrop.getX() + 0.5, targetCrop.getY(), targetCrop.getZ() + 0.5, 1.0);
            return;
        }

        harvestTick++;

        // Animation progressive de cassage (craquelures sur le bloc)
        if (entity.level() instanceof ServerLevel serverLevel) {
            int progress = (int) ((harvestTick / (float) HARVEST_DELAY) * 9);
            serverLevel.destroyBlockProgress(entity.getId(), targetCrop, progress);
        }

        if (harvestTick >= HARVEST_DELAY) {
            BlockState state = entity.level().getBlockState(targetCrop);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                entity.swing(InteractionHand.MAIN_HAND);
                entity.level().destroyBlock(targetCrop, true, entity);
                // Replanter avec l'état initial de la culture
                if (entity.level().getBlockState(targetCrop).isAir()) {
                    entity.level().setBlock(targetCrop, crop.defaultBlockState(), 3);
                }
            }

            // Chaînage : chercher la prochaine culture immédiatement
            BlockPos next = findMatureCrop();
            if (next != null) {
                targetCrop = next;
                harvestTick = 0;
                entity.getNavigation().moveTo(
                        targetCrop.getX() + 0.5, targetCrop.getY(), targetCrop.getZ() + 0.5, 1.0);
            } else {
                // Aucune culture trouvée : cooldown puis stop
                cooldown = COOLDOWN_TICKS;
                targetCrop = null;
                harvestTick = 0;
                entity.getNavigation().stop();
            }
        }
    }

    @Override
    public void stop() {
        if (cooldown == 0) cooldown = COOLDOWN_TICKS;
        targetCrop = null;
        harvestTick = 0;
        entity.getNavigation().stop();
    }

    private BlockPos findMatureCrop() {
        BlockPos origin = entity.blockPosition();
        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = entity.level().getBlockState(pos);
                    if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }
}
