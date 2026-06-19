package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.furranystudio.thefakeplayer.Config;
import org.furranystudio.thefakeplayer.Entity.Build.*;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakePlayerBuildGoal extends Goal {

    private final FakePlayerEntity entity;

    private enum BuildState { SELECTING_LOCATION, APPROACHING, BUILDING, COMPLETE, ABANDONED }
    private BuildState state = BuildState.SELECTING_LOCATION;

    @Nullable private StructureBlueprint blueprint = null;
    private int placeCooldown = 0;

    // Stuck detection: jump when navigation makes no progress
    private int navStuckTicks = 0;
    @Nullable private BlockPos lastBlockPos = null;

    // Scaffold: cooldown between scaffold block placements
    private int scaffoldCooldown = 0;

    // Short reach: entity must walk close to each block before placing
    private static final double PLACE_REACH_SQ = 9.0; // 3 blocks

    public FakePlayerBuildGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (entity.level().isClientSide()) return false;

        ConstructionTask task = entity.getActiveTask();

        // Resume an active non-abandoned task if close enough
        if (task != null && !task.isAbandoned()) {
            long abandonSq = (long) Config.ABANDON_BUILD_DISTANCE.get() * Config.ABANDON_BUILD_DISTANCE.get();
            if (task.getOrigin().distSqr(entity.blockPosition()) <= abandonSq) {
                loadBlueprint(task);
                if (blueprint != null) return true;
            }
            // Too far or blueprint missing — abandon
            task.setAbandoned(true);
            entity.setActiveTask(null);
        }

        // Start a new task only when far from all known bases
        long newBaseSq = (long) Config.NEW_BASE_DISTANCE.get() * Config.NEW_BASE_DISTANCE.get();
        if (entity.hasBaseNearby(newBaseSq)) return false;

        return HardcodedShelterBuilder.hasMaterials(entity.getInventory())
            || !StructureRegistry.get().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return state != BuildState.COMPLETE && state != BuildState.ABANDONED;
    }

    @Override
    public void start() {
        placeCooldown = 0;
        navStuckTicks = 0;
        lastBlockPos = null;
        scaffoldCooldown = 0;
        // Resume existing task rather than selecting a new location
        ConstructionTask existing = entity.getActiveTask();
        if (existing != null && !existing.isAbandoned() && blueprint != null) {
            state = BuildState.APPROACHING;
        } else {
            state = BuildState.SELECTING_LOCATION;
        }
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
    }

    @Override
    public void tick() {
        switch (state) {
            case SELECTING_LOCATION -> tickSelectLocation();
            case APPROACHING       -> tickApproach();
            case BUILDING          -> tickBuild();
            case COMPLETE          -> tickComplete();
        }
    }

    // ── States ───────────────────────────────────────────────────────────────

    private void tickSelectLocation() {
        StructureBlueprint selected = StructureRegistry.get().getRandomBlueprint(entity.getRandom());
        if (selected == null) {
            Block material = HardcodedShelterBuilder.chooseMaterial(entity.getInventory());
            if (material == null) { state = BuildState.ABANDONED; return; }
            selected = HardcodedShelterBuilder.build(material);
            StructureRegistry.get().registerRuntime(selected);
        }

        if (!hasRequiredMaterials(selected)) { state = BuildState.ABANDONED; return; }

        BlockPos origin = findBuildLocation(selected.getWidth(), selected.getDepth());
        if (origin == null) { state = BuildState.ABANDONED; return; }

        blueprint = selected;
        ConstructionTask task = new ConstructionTask(origin, selected.getId());
        entity.setActiveTask(task);

        state = BuildState.APPROACHING;
        entity.getNavigation().moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 1.0);
    }

    private void tickApproach() {
        ConstructionTask task = entity.getActiveTask();
        if (task == null || blueprint == null) { state = BuildState.ABANDONED; return; }

        long abandonSq = (long) Config.ABANDON_BUILD_DISTANCE.get() * Config.ABANDON_BUILD_DISTANCE.get();
        if (task.getOrigin().distSqr(entity.blockPosition()) > abandonSq) {
            task.setAbandoned(true);
            state = BuildState.ABANDONED;
            return;
        }

        if (entity.blockPosition().distSqr(task.getOrigin()) <= 16) {
            entity.getNavigation().stop();
            state = BuildState.BUILDING;
        } else {
            BlockPos o = task.getOrigin();
            entity.getNavigation().moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 1.0);
        }
    }

    private void tickBuild() {
        if (placeCooldown > 0) { placeCooldown--; return; }
        if (scaffoldCooldown > 0) scaffoldCooldown--;

        ConstructionTask task = entity.getActiveTask();
        if (task == null || blueprint == null) { state = BuildState.ABANDONED; return; }

        long abandonSq = (long) Config.ABANDON_BUILD_DISTANCE.get() * Config.ABANDON_BUILD_DISTANCE.get();
        if (task.getOrigin().distSqr(entity.blockPosition()) > abandonSq) {
            task.setAbandoned(true);
            state = BuildState.ABANDONED;
            return;
        }

        List<StructureBlueprint.PlacementEntry> blocks = blueprint.getBlocks();
        BlockPos origin = task.getOrigin();

        // 1. Place any block within reach
        for (int i = 0; i < blocks.size(); i++) {
            if (task.getPlacedIndices().contains(i)) continue;
            StructureBlueprint.PlacementEntry entry = blocks.get(i);
            BlockPos worldPos = origin.offset(entry.offset());
            BlockState current = entity.level().getBlockState(worldPos);

            // Already the correct block
            if (statesMatch(current, entry.state())) {
                task.markPlaced(i);
                continue;
            }

            // Door/bed second halves: skip — placed together with the first half
            if (isDoorUpperHalf(entry.state()) || isBedHeadHalf(entry.state())) {
                task.markPlaced(i);
                continue;
            }

            // Scaffold up: horizontally close but too high to reach — jump + place block under self
            if (scaffoldCooldown == 0) {
                BlockPos feet = entity.blockPosition();
                int hdx = worldPos.getX() - feet.getX();
                int hdz = worldPos.getZ() - feet.getZ();
                if (hdx * hdx + hdz * hdz <= 4 && worldPos.getY() > feet.getY() + 1) {
                    if (placeScaffoldUnderSelf()) return;
                }
            }

            if (entity.blockPosition().distSqr(worldPos) > PLACE_REACH_SQ) continue;

            entity.getLookControl().setLookAt(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);

            // Never place a block at the entity's own position — would cause suffocation
            BlockPos entFeet = entity.blockPosition();
            if (worldPos.equals(entFeet) || worldPos.equals(entFeet.above())) {
                entity.getJumpControl().jump();
                int ox = entity.getRandom().nextIntBetweenInclusive(-1, 1);
                int oz = entity.getRandom().nextIntBetweenInclusive(-1, 1);
                entity.getNavigation().moveTo(entFeet.getX() + ox + 0.5, entFeet.getY(), entFeet.getZ() + oz + 0.5, 1.4);
                return;
            }

            // Solid blocker in the way — equip the appropriate tool and mine it
            if (!current.isAir()) {
                equipToolForMining(current);
                entity.triggerSwingAnim();
                entity.level().destroyBlock(worldPos, true);
                placeCooldown = 10;
                return;
            }

            Item needed = blockToItem(entry.state());
            if (needed == null || needed == Items.AIR) {
                task.markPlaced(i);
                continue;
            }

            // Wall torch: defer until the support wall block is solid
            if (isWallTorch(entry.state())) {
                Direction facing = entry.state().getValue(BlockStateProperties.HORIZONTAL_FACING);
                BlockPos supportPos = worldPos.relative(facing.getOpposite());
                if (!entity.level().getBlockState(supportPos).isSolid()) {
                    continue;
                }
            }

            // Door lower half: place both halves at once with one item
            if (isDoorLowerHalf(entry.state())) {
                if (!hasItem(needed)) continue;
                BlockPos upperPos = worldPos.above();
                BlockState upperCurrent = entity.level().getBlockState(upperPos);
                if (!upperCurrent.isAir() && !statesMatch(upperCurrent, entry.state())) {
                    equipToolForMining(upperCurrent);
                    entity.triggerSwingAnim();
                    entity.level().destroyBlock(upperPos, true);
                    placeCooldown = 10;
                    return;
                }
                BlockState upperState = entry.state()
                    .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
                equipItemForPlacement(findItemStack(needed));
                entity.level().setBlock(worldPos, entry.state(), Block.UPDATE_ALL);
                entity.level().setBlock(upperPos, upperState, Block.UPDATE_ALL);
                consumeItem(needed);
                playPlaceSound(worldPos, entry.state());
                task.markPlaced(i);
                placeCooldown = placeCooldownTicks();
                entity.triggerSwingAnim();
                return;
            }

            // Bed foot: place both foot and head using any bed from inventory
            if (isBedFootHalf(entry.state())) {
                Item bedItem = findAnyBedItem();
                if (bedItem == null) {
                    task.markPlaced(i); // no bed in inventory — skip
                    continue;
                }
                Block bedBlock = ((BlockItem) bedItem).getBlock();
                Direction bedFacing = entry.state().getValue(BlockStateProperties.HORIZONTAL_FACING);
                BlockPos headPos = worldPos.relative(bedFacing);
                BlockState headCurrent = entity.level().getBlockState(headPos);
                if (!headCurrent.isAir() && !statesMatch(headCurrent, entry.state())) {
                    equipToolForMining(headCurrent);
                    entity.triggerSwingAnim();
                    entity.level().destroyBlock(headPos, true);
                    placeCooldown = 10;
                    return;
                }
                BlockState footState = bedBlock.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, bedFacing)
                    .setValue(BlockStateProperties.BED_PART, BedPart.FOOT);
                BlockState headState = bedBlock.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, bedFacing)
                    .setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
                equipItemForPlacement(findItemStack(bedItem));
                entity.level().setBlock(worldPos, footState, Block.UPDATE_ALL);
                entity.level().setBlock(headPos, headState, Block.UPDATE_ALL);
                consumeItem(bedItem);
                playPlaceSound(worldPos, footState);
                task.markPlaced(i);
                placeCooldown = placeCooldownTicks();
                entity.triggerSwingAnim();
                return;
            }

            if (!hasItem(needed)) continue;

            equipItemForPlacement(findItemStack(needed));
            entity.level().setBlock(worldPos, entry.state(), Block.UPDATE_ALL);
            consumeItem(needed);
            playPlaceSound(worldPos, entry.state());
            task.markPlaced(i);
            placeCooldown = placeCooldownTicks();
            entity.triggerSwingAnim();
            return;
        }

        // 2. Navigate to the nearest unplaced block we actually have items for
        BlockPos nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < blocks.size(); i++) {
            if (task.getPlacedIndices().contains(i)) continue;
            StructureBlueprint.PlacementEntry entry = blocks.get(i);
            if (isDoorUpperHalf(entry.state()) || isBedHeadHalf(entry.state())) continue;
            if (isBedFootHalf(entry.state())) {
                if (findAnyBedItem() == null) continue;
            } else {
                Item needed = blockToItem(entry.state());
                if (needed == null || !hasItem(needed)) continue;
            }
            BlockPos worldPos = origin.offset(entry.offset());
            double d = entity.blockPosition().distSqr(worldPos);
            if (d < bestDist) { bestDist = d; nearest = worldPos; }
        }

        if (nearest == null) {
            // All blocks processed — register base immediately
            tickComplete();
        } else {
            // Stuck detection: jump to escape when navigation stalls
            if (entity.blockPosition().equals(lastBlockPos)) {
                navStuckTicks++;
                if (navStuckTicks >= 60) { // ~3 seconds without moving → jump
                    entity.getJumpControl().jump();
                    navStuckTicks = 0;
                }
            } else {
                navStuckTicks = 0;
                lastBlockPos = entity.blockPosition().immutable();
            }
            entity.getNavigation().moveTo(nearest.getX() + 0.5, nearest.getY(), nearest.getZ() + 0.5, 1.0);
        }
    }

    private void tickComplete() {
        ConstructionTask task = entity.getActiveTask();
        if (task != null && blueprint != null) {
            BlockPos center = task.getOrigin().offset(
                blueprint.getWidth() / 2, 1, blueprint.getDepth() / 2);
            BaseRecord base = new BaseRecord(center);
            base.setComplete(true);
            for (StructureBlueprint.PlacementEntry entry : blueprint.getBlocks()) {
                if (isBedFootHalf(entry.state())) {
                    BlockPos worldPos = task.getOrigin().offset(entry.offset());
                    BlockState actual = entity.level().getBlockState(worldPos);
                    if (actual.hasProperty(BlockStateProperties.BED_PART)
                            && actual.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
                        base.setBedPos(worldPos);
                        break;
                    }
                }
            }
            entity.addBase(base);
        }
        entity.setActiveTask(null);
        blueprint = null;
        entity.sendContextualMessage(
            "thefakeplayer.chat.build.done.0",
            "thefakeplayer.chat.build.done.1"
        );
        state = BuildState.COMPLETE;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean placeScaffoldUnderSelf() {
        Item scaffoldItem = findScaffoldItem();
        if (scaffoldItem == null) return false;
        BlockPos feetPos = entity.blockPosition();
        if (isBuildPosition(feetPos)) return false;
        if (!entity.level().getBlockState(feetPos).isAir()) return false;
        Block block = ((BlockItem) scaffoldItem).getBlock();
        entity.getJumpControl().jump();
        entity.level().setBlock(feetPos, block.defaultBlockState(), Block.UPDATE_ALL);
        consumeItem(scaffoldItem);
        entity.triggerSwingAnim();
        scaffoldCooldown = 15;
        return true;
    }

    @Nullable
    private Item findScaffoldItem() {
        for (Item candidate : new Item[]{ Items.SCAFFOLDING, Items.DIRT }) {
            if (hasItem(candidate)) return candidate;
        }
        return null;
    }

    private boolean isBuildPosition(BlockPos pos) {
        ConstructionTask task = entity.getActiveTask();
        if (task == null || blueprint == null) return false;
        for (StructureBlueprint.PlacementEntry entry : blueprint.getBlocks()) {
            if (task.getOrigin().offset(entry.offset()).equals(pos)) return true;
        }
        return false;
    }

    private boolean hasRequiredMaterials(StructureBlueprint bp) {
        Map<Item, Integer> needed = new HashMap<>();
        int bedsNeeded = 0;
        for (StructureBlueprint.PlacementEntry entry : bp.getBlocks()) {
            BlockState state = entry.state();
            if (isDoorUpperHalf(state) || isBedHeadHalf(state)) continue;
            if (isBedFootHalf(state)) { bedsNeeded++; continue; }
            Item item = blockToItem(state);
            if (item == null || item == Items.AIR) continue;
            needed.merge(item, 1, Integer::sum);
        }
        for (Map.Entry<Item, Integer> e : needed.entrySet()) {
            if (countItem(e.getKey()) < e.getValue()) return false;
        }
        return bedsNeeded == 0 || countAnyBed() >= bedsNeeded;
    }

    private int countItem(Item item) {
        int total = 0;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    private int countAnyBed() {
        int total = 0;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi
                    && bi.getBlock().defaultBlockState().hasProperty(BlockStateProperties.BED_PART)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void equipItemForPlacement(ItemStack stack) {
        if (!stack.isEmpty()) {
            entity.setItemInHand(InteractionHand.MAIN_HAND, stack.copyWithCount(1));
        }
    }

    private void equipToolForMining(BlockState blocker) {
        boolean isWood = blocker.is(BlockTags.LOGS) || blocker.is(BlockTags.PLANKS)
            || blocker.is(BlockTags.WOODEN_SLABS) || blocker.is(BlockTags.WOODEN_DOORS)
            || blocker.is(BlockTags.WOODEN_FENCES) || blocker.is(BlockTags.WOODEN_STAIRS);
        Class<? extends DiggerItem> preferred = isWood ? AxeItem.class : PickaxeItem.class;
        Class<? extends DiggerItem> fallback   = isWood ? PickaxeItem.class : AxeItem.class;
        if (equipToolOfType(preferred)) return;
        if (equipToolOfType(fallback))  return;
        // No specific tool — use any digger or bare hands
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof DiggerItem) {
                entity.setItemInHand(InteractionHand.MAIN_HAND, stack.copyWithCount(1));
                return;
            }
        }
        entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private boolean equipToolOfType(Class<? extends DiggerItem> toolClass) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && toolClass.isInstance(stack.getItem())) {
                entity.setItemInHand(InteractionHand.MAIN_HAND, stack.copyWithCount(1));
                return true;
            }
        }
        return false;
    }

    private void loadBlueprint(ConstructionTask task) {
        blueprint = StructureRegistry.get().getBlueprintById(task.getBlueprintId());
        if (blueprint == null && "hardcoded_shelter".equals(task.getBlueprintId())) {
            Block material = HardcodedShelterBuilder.chooseMaterial(entity.getInventory());
            if (material != null) {
                blueprint = HardcodedShelterBuilder.build(material);
                StructureRegistry.get().registerRuntime(blueprint);
            }
        }
    }

    @Nullable
    private BlockPos findBuildLocation(int width, int depth) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = entity.getRandom().nextIntBetweenInclusive(-16, 16);
            int dz = entity.getRandom().nextIntBetweenInclusive(-16, 16);
            BlockPos candidate = entity.blockPosition().offset(dx, 0, dz);
            BlockPos ground = findGround(candidate);
            if (ground != null && isAreaSuitable(ground, width, depth)) return ground;
        }
        return null;
    }

    @Nullable
    private BlockPos findGround(BlockPos pos) {
        for (int dy = 5; dy >= -10; dy--) {
            BlockPos at = pos.offset(0, dy, 0);
            if (entity.level().getBlockState(at.below()).isSolid()
                    && entity.level().getBlockState(at).isAir()) return at;
        }
        return null;
    }

    private boolean isAreaSuitable(BlockPos origin, int width, int depth) {
        int minY = origin.getY(), maxY = origin.getY();
        for (int x = 0; x < width; x += width - 1) {
            for (int z = 0; z < depth; z += depth - 1) {
                BlockPos corner = findGround(origin.offset(x, 0, z));
                if (corner == null) return false;
                minY = Math.min(minY, corner.getY());
                maxY = Math.max(maxY, corner.getY());
            }
        }
        return (maxY - minY) <= 3;
    }

    private boolean statesMatch(BlockState world, BlockState expected) {
        // Any bed matches any other bed (player may have a different color than blueprint's default)
        if (expected.hasProperty(BlockStateProperties.BED_PART)) {
            return world.hasProperty(BlockStateProperties.BED_PART)
                && world.getValue(BlockStateProperties.BED_PART) == expected.getValue(BlockStateProperties.BED_PART);
        }
        return world.getBlock() == expected.getBlock();
    }

    @Nullable
    private Item blockToItem(BlockState state) {
        if (state.getBlock() == Blocks.WALL_TORCH) return Items.TORCH;
        if (state.getBlock() == Blocks.SOUL_WALL_TORCH) return Items.SOUL_TORCH;
        // For bed entries: return whatever bed is in inventory (any color)
        if (state.hasProperty(BlockStateProperties.BED_PART)) return findAnyBedItem();
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    @Nullable
    private Item findAnyBedItem() {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi
                    && bi.getBlock().defaultBlockState().hasProperty(BlockStateProperties.BED_PART)) {
                return stack.getItem();
            }
        }
        return null;
    }

    private boolean isDoorLowerHalf(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
    }

    private boolean isDoorUpperHalf(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
    }

    private boolean isBedFootHalf(BlockState state) {
        return state.hasProperty(BlockStateProperties.BED_PART)
            && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT;
    }

    private boolean isBedHeadHalf(BlockState state) {
        return state.hasProperty(BlockStateProperties.BED_PART)
            && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    private boolean isWallTorch(BlockState state) {
        return state.getBlock() == Blocks.WALL_TORCH || state.getBlock() == Blocks.SOUL_WALL_TORCH;
    }

    private boolean hasItem(Item item) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) return true;
        }
        return false;
    }

    private ItemStack findItemStack(Item item) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) return stack;
        }
        return ItemStack.EMPTY;
    }

    private void consumeItem(Item item) {
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                stack.shrink(1);
                return;
            }
        }
    }

    private void playPlaceSound(BlockPos pos, BlockState state) {
        net.minecraft.world.level.block.SoundType sound = state.getSoundType();
        entity.level().playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
    }

    private int placeCooldownTicks() {
        int bps = Config.BUILD_BLOCKS_PER_SECOND.get();
        return Math.max(1, 20 / bps);
    }
}
