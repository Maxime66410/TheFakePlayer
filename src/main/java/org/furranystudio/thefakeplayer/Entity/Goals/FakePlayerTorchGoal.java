package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerTorchGoal extends Goal {

    private final FakePlayerEntity entity;
    private BlockPos placementPos = null;
    private BlockState placementState = null;
    private int torchSlot = -1;
    private Vec3 lastPlacedPos = null;
    private int placeTick = 0;

    private static final int PLACE_DELAY = 10;
    private static final double MIN_DIST_SQ = 64.0; // 8 blocks between torches

    public FakePlayerTorchGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (lastPlacedPos != null && entity.position().distanceToSqr(lastPlacedPos) < MIN_DIST_SQ) return false;
        if (entity.level().isDay() && entity.level().canSeeSky(entity.blockPosition().above())) return false;
        torchSlot = findTorchSlot();
        if (torchSlot < 0) return false;
        if (!isDarkNearby()) return false;
        return findPlacement();
    }

    @Override
    public boolean canContinueToUse() {
        return placementPos != null && placeTick <= PLACE_DELAY;
    }

    @Override
    public void start() {
        placeTick = 0;
        // Move full torch stack from inventory to hand (like MineGoal with its tool)
        ItemStack torchStack = entity.getInventory().getItem(torchSlot).copy();
        entity.setItemInHand(InteractionHand.MAIN_HAND, torchStack);
        entity.getInventory().setItem(torchSlot, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        if (placementPos == null) return;
        entity.getLookControl().setLookAt(placementPos.getX() + 0.5, placementPos.getY() + 0.5, placementPos.getZ() + 0.5);
        placeTick++;
        if (placeTick == PLACE_DELAY / 2) entity.triggerSwingAnim();
        if (placeTick >= PLACE_DELAY) {
            placeBlock();
            lastPlacedPos = entity.position();
            placementPos = null;
        }
    }

    @Override
    public void stop() {
        // Return remaining torches from hand to inventory, then clear hand
        ItemStack hand = entity.getMainHandItem();
        if (!hand.isEmpty() && isTorchItem(hand.getItem())) {
            entity.getInventory().addItem(hand);
        }
        entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        placementPos = null;
        placeTick = 0;
    }

    private void placeBlock() {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.getBlockState(placementPos).isAir()) return;
        serverLevel.setBlock(placementPos, placementState, 3);
        var sound = placementState.getSoundType();
        serverLevel.playSound(null, placementPos, sound.getPlaceSound(), SoundSource.BLOCKS, 1.0f, sound.getPitch());
        entity.getMainHandItem().shrink(1);
    }

    private boolean findPlacement() {
        Item torchItem = entity.getInventory().getItem(torchSlot).getItem();
        BlockPos feet = entity.blockPosition();

        // Wall preferred: head level first, then above head, then feet as last resort
        BlockPos[] wallHeights = { feet.above(), feet.above().above(), feet };
        for (BlockPos torchPos : wallHeights) {
            if (hasTorchNearby(torchPos)) return false;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos wall = torchPos.relative(dir);
                Direction faceTowardEntity = dir.getOpposite();
                if (entity.level().getBlockState(wall).isFaceSturdy(entity.level(), wall, faceTowardEntity)
                        && entity.level().getBlockState(torchPos).isAir()) {
                    placementPos = torchPos;
                    placementState = (torchItem == Items.SOUL_TORCH ? Blocks.SOUL_WALL_TORCH : Blocks.WALL_TORCH)
                            .defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, faceTowardEntity);
                    return true;
                }
            }
        }

        // Floor fallback: block below has sturdy top face, air at feet
        if (!hasTorchNearby(feet)
                && entity.level().getBlockState(feet.below()).isFaceSturdy(entity.level(), feet.below(), Direction.UP)
                && entity.level().getBlockState(feet).isAir()) {
            placementPos = feet;
            placementState = torchItem == Items.SOUL_TORCH
                    ? Blocks.SOUL_TORCH.defaultBlockState()
                    : Blocks.TORCH.defaultBlockState();
            return true;
        }

        return false;
    }

    private boolean isDarkNearby() {
        BlockPos origin = entity.blockPosition();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos check = origin.offset(dx, 0, dz);
                // Dark if globally unlit (no sun, no nearby torch) AND not covered by a block light source
                if (entity.level().getMaxLocalRawBrightness(check) < 7
                        && entity.level().getBrightness(LightLayer.BLOCK, check) < 7) return true;
            }
        }
        return false;
    }

    private int findTorchSlot() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (isTorchItem(stack.getItem())) return i;
        }
        return -1;
    }

    private boolean hasTorchNearby(BlockPos center) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    Block b = entity.level().getBlockState(center.offset(dx, dy, dz)).getBlock();
                    if (b == Blocks.TORCH || b == Blocks.WALL_TORCH
                            || b == Blocks.SOUL_TORCH || b == Blocks.SOUL_WALL_TORCH) return true;
                }
            }
        }
        return false;
    }

    private boolean isTorchItem(Item item) {
        return item == Items.TORCH || item == Items.SOUL_TORCH;
    }
}
