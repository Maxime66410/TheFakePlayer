package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;
import java.util.Set;

public class FakePlayerMineGoal extends Goal {

    private final FakePlayerEntity entity;
    private BlockPos targetBlock = null;
    private int miningTick = 0;
    private int breakTime = 20;
    private int cooldown = 0;
    private int equippedToolSlot = -1; // inventory slot, -1 = no tool

    private static final int SEARCH_RANGE = 8;
    private static final int COOLDOWN_MINED = 200;
    private static final int COOLDOWN_NONE = 300;
    private static final float MAX_HARDNESS = 30.0f; // ancient debris = 30.0, obsidian = 50.0 (exclu)

    private static final Set<Block> STONE_BLOCKS = Set.of(
        Blocks.STONE, Blocks.COBBLESTONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
        Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL
    );

    private static final Set<Block> EXTRA_ORE_BLOCKS = Set.of(
        Blocks.DEEPSLATE_COAL_ORE,
        Blocks.DEEPSLATE_IRON_ORE,
        Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.NETHER_QUARTZ_ORE,
        Blocks.NETHER_GOLD_ORE,
        Blocks.ANCIENT_DEBRIS
    );

    public FakePlayerMineGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        targetBlock = findTargetBlock();
        return targetBlock != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetBlock == null) return false;
        BlockState state = entity.level().getBlockState(targetBlock);
        return !state.isAir() && isTargetable(state, targetBlock);
    }

    @Override
    public void start() {
        miningTick = 0;
        BlockState state = entity.level().getBlockState(targetBlock);
        equippedToolSlot = selectBestToolSlot(state);
        if (equippedToolSlot >= 0) {
            // Move the tool from inventory to hand — no copy, the original is in hand
            ItemStack tool = entity.getInventory().getItem(equippedToolSlot);
            entity.setItemInHand(InteractionHand.MAIN_HAND, tool);
            entity.getInventory().setItem(equippedToolSlot, ItemStack.EMPTY);
        }
        breakTime = computeBreakTime(state, entity.getMainHandItem());
        entity.getNavigation().moveTo(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        if (targetBlock == null) return;

        // Always look at the target block, even while approaching
        entity.getLookControl().setLookAt(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);

        if (entity.blockPosition().distSqr(targetBlock) > 6.25) {
            entity.getNavigation().moveTo(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 1.0);
            return;
        }

        entity.getNavigation().stop();

        miningTick++;

        // Crack overlay: stages 0-9 proportional to progress
        if (entity.level() instanceof ServerLevel serverLevel) {
            int stage = Math.min(9, (int)((float) miningTick / breakTime * 10));
            serverLevel.destroyBlockProgress(entity.getId(), targetBlock, stage);
        }

        if (miningTick % 5 == 0) {
            entity.triggerSwingAnim();
        }

        if (miningTick % 4 == 0) {
            BlockState state = entity.level().getBlockState(targetBlock);
            var soundType = state.getSoundType();
            entity.level().playSound(null, targetBlock, soundType.getHitSound(),
                SoundSource.BLOCKS, 0.3f, soundType.getPitch() * 0.8f);
        }

        if (miningTick >= breakTime) {
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.destroyBlockProgress(entity.getId(), targetBlock, -1);
                BlockState state = entity.level().getBlockState(targetBlock);
                if (!state.isAir()) {
                    entity.triggerSwingAnim();
                    serverLevel.destroyBlock(targetBlock, true, entity);
                }
            }

            BlockPos next = findTargetBlock();
            if (next != null) {
                targetBlock = next;
                miningTick = 0;
                // Keep the tool in hand for the next block, just recompute break time
                breakTime = computeBreakTime(entity.level().getBlockState(targetBlock), entity.getMainHandItem());
                entity.getNavigation().moveTo(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 1.0);
            } else {
                cooldown = COOLDOWN_MINED;
                targetBlock = null;
            }
        }
    }

    @Override
    public void stop() {
        if (targetBlock != null && entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(entity.getId(), targetBlock, -1);
        }
        if (cooldown == 0) cooldown = COOLDOWN_NONE;

        // Return tool from hand to inventory before clearing the hand
        returnToolToInventory();
        entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        targetBlock = null;
        miningTick = 0;
        equippedToolSlot = -1;
        entity.getNavigation().stop();
    }

    // Returns the tool (if any) from hand back to its original inventory slot
    private void returnToolToInventory() {
        ItemStack hand = entity.getMainHandItem();
        if (hand.isEmpty() || !(hand.getItem() instanceof DiggerItem)) return;
        if (equippedToolSlot >= 0 && entity.getInventory().getItem(equippedToolSlot).isEmpty()) {
            entity.getInventory().setItem(equippedToolSlot, hand);
        } else {
            entity.getInventory().addItem(hand);
        }
    }

    private BlockPos findTargetBlock() {
        ItemStack hand = entity.getMainHandItem();
        boolean hasPickaxe;
        boolean hasAxe;

        if (hand.getItem() instanceof PickaxeItem) {
            hasPickaxe = true; hasAxe = false;
        } else if (hand.getItem() instanceof AxeItem) {
            hasPickaxe = false; hasAxe = true;
        } else {
            // Empty hand: check inventory
            hasPickaxe = hasToolInInventory(PickaxeItem.class);
            hasAxe = hasToolInInventory(AxeItem.class);
        }

        BlockPos origin = entity.blockPosition();
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = entity.level().getBlockState(pos);

                    if (!isTargetable(state, pos)) continue;
                    if (!isExposed(pos)) continue;

                    boolean isLog = state.is(BlockTags.LOGS);
                    boolean isPickaxeBlock = isPickaxeBlock(state);

                    boolean matches = (!hasPickaxe && !hasAxe && isLog)
                        || (hasPickaxe && isPickaxeBlock)
                        || (hasAxe && isLog);

                    if (matches) {
                        double dist = origin.distSqr(pos);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestPos = pos.immutable();
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    // Checks that at least one face of the block is exposed to air (block is accessible)
    private boolean isExposed(BlockPos pos) {
        return entity.level().getBlockState(pos.above()).isAir()
            || entity.level().getBlockState(pos.below()).isAir()
            || entity.level().getBlockState(pos.north()).isAir()
            || entity.level().getBlockState(pos.south()).isAir()
            || entity.level().getBlockState(pos.east()).isAir()
            || entity.level().getBlockState(pos.west()).isAir();
    }

    private boolean isTargetable(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        float hardness = state.getDestroySpeed(entity.level(), pos);
        return hardness >= 0 && hardness <= MAX_HARDNESS;
    }

    private boolean isPickaxeBlock(BlockState state) {
        return STONE_BLOCKS.contains(state.getBlock())
            || EXTRA_ORE_BLOCKS.contains(state.getBlock())
            || state.is(BlockTags.COAL_ORES)
            || state.is(BlockTags.IRON_ORES)
            || state.is(BlockTags.COPPER_ORES)
            || state.is(BlockTags.GOLD_ORES)
            || state.is(BlockTags.DIAMOND_ORES)
            || state.is(BlockTags.EMERALD_ORES)
            || state.is(BlockTags.LAPIS_ORES)
            || state.is(BlockTags.REDSTONE_ORES);
    }

    private boolean hasToolInInventory(Class<? extends DiggerItem> toolClass) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (toolClass.isInstance(entity.getInventory().getItem(i).getItem())) return true;
        }
        return false;
    }

    private int selectBestToolSlot(BlockState state) {
        boolean isLog = state.is(BlockTags.LOGS);
        Class<? extends DiggerItem> preferred = isLog ? AxeItem.class : PickaxeItem.class;
        Class<? extends DiggerItem> fallback   = isLog ? PickaxeItem.class : AxeItem.class;

        int slot = findBestToolSlot(preferred, state);
        if (slot < 0) slot = findBestToolSlot(fallback, state);
        return slot;
    }

    private int findBestToolSlot(Class<? extends DiggerItem> toolClass, BlockState state) {
        int bestSlot = -1;
        float bestScore = 1.0f; // only equip if strictly faster than bare hands

        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!toolClass.isInstance(stack.getItem())) continue;

            float speed = stack.getDestroySpeed(state);
            int effLevel = getEfficiencyLevel(stack);
            if (effLevel > 0) speed += effLevel * effLevel + 1;

            if (speed > bestScore) {
                bestScore = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int computeBreakTime(BlockState state, ItemStack tool) {
        BlockPos pos = targetBlock != null ? targetBlock : entity.blockPosition();
        float hardness = state.getDestroySpeed(entity.level(), pos);
        if (hardness <= 0) return 5;

        float speed = 1.0f;
        if (!tool.isEmpty()) {
            float toolSpeed = tool.getDestroySpeed(state);
            int effLevel = getEfficiencyLevel(tool);
            if (effLevel > 0) toolSpeed += effLevel * effLevel + 1;
            if (toolSpeed > speed) speed = toolSpeed;
        }
        return Math.max(5, (int)(hardness * 20 / speed));
    }

    private int getEfficiencyLevel(ItemStack stack) {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchants.keySet()) {
            if (holder.is(Enchantments.EFFICIENCY)) {
                return enchants.getLevel(holder);
            }
        }
        return 0;
    }
}
