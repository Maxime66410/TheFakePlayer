package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerBridgeGoal extends Goal {

    private final FakePlayerEntity entity;
    private int blocksPlaced = 0;
    private static final int MAX_BRIDGE = 15;

    private static final Block[] BRIDGE_BLOCKS = {
        Blocks.COBBLESTONE, Blocks.STONE, Blocks.COBBLED_DEEPSLATE,
        Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE,
        Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
        Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
        Blocks.DIRT, Blocks.GRAVEL, Blocks.SAND
    };

    public FakePlayerBridgeGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (entity.level().isClientSide()) return false;
        if (!entity.getNavigation().isInProgress()) return false;
        if (blocksPlaced >= MAX_BRIDGE) return false;
        return isGapAhead() && hasBridgeBlock();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getNavigation().isInProgress()
            && blocksPlaced < MAX_BRIDGE
            && hasBridgeBlock();
    }

    @Override
    public void start() {
        blocksPlaced = 0;
    }

    @Override
    public void stop() {
        blocksPlaced = 0;
    }

    @Override
    public void tick() {
        int[] dir = getMovementDir();
        int dx = dir[0], dz = dir[1];

        if (dx != 0 && dz != 0) {
            // Diagonal: lay L-shape — cardinal X, then cardinal Z, then diagonal
            placeDiagonalBridge(dx, dz);
        } else if (dx != 0 || dz != 0) {
            BlockPos aheadBelow = entity.blockPosition().offset(dx, -1, dz);
            if (entity.level().getBlockState(aheadBelow).isAir()) {
                placeAt(aheadBelow);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void placeDiagonalBridge(int dx, int dz) {
        BlockPos pos = entity.blockPosition();
        BlockPos cardX = pos.offset(dx, -1, 0);
        BlockPos cardZ = pos.offset(0,  -1, dz);
        BlockPos diag  = pos.offset(dx, -1, dz);
        // Place cardinal supports first so the diagonal block is never floating
        if (entity.level().getBlockState(cardX).isAir()) { placeAt(cardX); return; }
        if (entity.level().getBlockState(cardZ).isAir()) { placeAt(cardZ); return; }
        if (entity.level().getBlockState(diag).isAir())  { placeAt(diag); }
    }

    private void placeAt(BlockPos pos) {
        Block bridge = findBridgeBlock();
        if (bridge == null) return;
        BlockState state = bridge.defaultBlockState();
        entity.level().setBlock(pos, state, Block.UPDATE_ALL);
        consumeBlock(bridge);
        blocksPlaced++;
        net.minecraft.world.level.block.SoundType sound = state.getSoundType();
        entity.level().playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        entity.triggerSwingAnim();
    }

    // Returns true if a bridge is needed in the current movement direction
    private boolean isGapAhead() {
        int[] dir = getMovementDir();
        int dx = dir[0], dz = dir[1];
        BlockPos feet = entity.blockPosition();

        if (dx != 0 && dz != 0) {
            // Diagonal: bridge needed if any L-path position is a deep gap
            return isDeepGap(feet.offset(dx, -1, 0))
                || isDeepGap(feet.offset(0, -1, dz))
                || isDeepGap(feet.offset(dx, -1, dz));
        }

        // Cardinal: require all 3 consecutive floor positions to be deep gaps
        int gapCount = 0;
        for (int i = 1; i <= 3; i++) {
            if (isDeepGap(feet.offset(dx * i, -1, dz * i))) gapCount++;
        }
        return gapCount >= 3;
    }

    // A "deep gap" is missing floor block with at least 3 air blocks below
    private boolean isDeepGap(BlockPos floorPos) {
        return entity.level().getBlockState(floorPos).isAir()
            && entity.level().getBlockState(floorPos.below()).isAir()
            && entity.level().getBlockState(floorPos.below().below()).isAir();
    }

    // Returns [dx, dz] from current motion or yaw fallback
    private int[] getMovementDir() {
        Vec3 motion = entity.getDeltaMovement();
        int dx, dz;
        if (motion.horizontalDistanceSqr() < 0.001) {
            float yaw = entity.getYRot();
            double rad = Math.toRadians(yaw);
            dx = (int) Math.round(-Math.sin(rad));
            dz = (int) Math.round(Math.cos(rad));
        } else {
            double len = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            dx = (int) Math.round(motion.x / len);
            dz = (int) Math.round(motion.z / len);
        }
        return new int[]{dx, dz};
    }

    private boolean hasBridgeBlock() {
        return findBridgeBlock() != null;
    }

    private Block findBridgeBlock() {
        for (Block block : BRIDGE_BLOCKS) {
            if (hasBlock(block)) return block;
        }
        return null;
    }

    private boolean hasBlock(Block block) {
        Item item = block.asItem();
        if (item == Items.AIR) return false;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) return true;
        }
        return false;
    }

    private void consumeBlock(Block block) {
        Item item = block.asItem();
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                stack.shrink(1);
                return;
            }
        }
    }
}
