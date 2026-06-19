package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.world.item.Items.*;

public class FakePlayerCraftGoal extends Goal {

    private final FakePlayerEntity entity;
    private int cooldown = 0;
    private int craftTick = 0;
    private boolean done = false;

    private enum CraftState { CRAFTING_2x2, PLACING_TABLE, APPROACHING_TABLE, CRAFTING_3x3 }
    private CraftState state = CraftState.CRAFTING_2x2;
    private CraftRecipe pendingRecipe = null;
    private BlockPos tablePos = null;

    private static final int TABLE_SEARCH_RANGE = 50;
    private static final int CRAFT_DURATION = 40;
    private static final int COOLDOWN = 300;

    // ── Recipe system ─────────────────────────────────────────────────────────

    // Each slot is an array of acceptable items (OR logic); null = empty slot
    record CraftRecipe(ItemStack result, int gridW, int gridH, Item[][] slots) {
        boolean needsTable() { return gridW > 2 || gridH > 2; }
    }

    private static final Item[] ANY_PLANK = {
        OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, JUNGLE_PLANKS, ACACIA_PLANKS,
        DARK_OAK_PLANKS, MANGROVE_PLANKS, CHERRY_PLANKS, BAMBOO_PLANKS,
        CRIMSON_PLANKS, WARPED_PLANKS
    };
    private static final Item[] ANY_COBBLE = {
        COBBLESTONE, COBBLED_DEEPSLATE, ANDESITE, DIORITE, GRANITE
    };
    private static final Item[] S = { STICK };

    private static Item[] one(Item item) { return new Item[]{ item }; }

    private static final Item[] PICKAXE_TIERS = { WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE };
    private static final Item[] SWORD_TIERS   = { WOODEN_SWORD,   STONE_SWORD,   IRON_SWORD,   DIAMOND_SWORD,   NETHERITE_SWORD   };
    private static final Item[] AXE_TIERS     = { WOODEN_AXE,     STONE_AXE,     IRON_AXE,     DIAMOND_AXE,     NETHERITE_AXE     };

    // Log → Plank mapping (any wood type)
    private static final Item[] LOGS = {
        OAK_LOG, OAK_WOOD,
        SPRUCE_LOG, SPRUCE_WOOD,
        BIRCH_LOG, BIRCH_WOOD,
        JUNGLE_LOG, JUNGLE_WOOD,
        ACACIA_LOG, ACACIA_WOOD,
        DARK_OAK_LOG, DARK_OAK_WOOD,
        MANGROVE_LOG, MANGROVE_WOOD,
        CHERRY_LOG, CHERRY_WOOD,
        BAMBOO_BLOCK,
        CRIMSON_STEM, CRIMSON_HYPHAE,
        WARPED_STEM,  WARPED_HYPHAE
    };
    private static final Item[] PLANKS_FOR_LOG = {
        OAK_PLANKS,     OAK_PLANKS,
        SPRUCE_PLANKS,  SPRUCE_PLANKS,
        BIRCH_PLANKS,   BIRCH_PLANKS,
        JUNGLE_PLANKS,  JUNGLE_PLANKS,
        ACACIA_PLANKS,  ACACIA_PLANKS,
        DARK_OAK_PLANKS, DARK_OAK_PLANKS,
        MANGROVE_PLANKS, MANGROVE_PLANKS,
        CHERRY_PLANKS,  CHERRY_PLANKS,
        BAMBOO_PLANKS,
        CRIMSON_PLANKS, CRIMSON_PLANKS,
        WARPED_PLANKS,  WARPED_PLANKS
    };

    // Priority-ordered recipe list (2x2 first, then 3x3 by tool tier)
    private static final List<CraftRecipe> RECIPES = List.of(
        // 2x2 — no table needed
        new CraftRecipe(new ItemStack(STICK, 4), 1, 2,
            new Item[][]{ ANY_PLANK, ANY_PLANK }),
        new CraftRecipe(new ItemStack(CRAFTING_TABLE, 1), 2, 2,
            new Item[][]{ ANY_PLANK, ANY_PLANK, ANY_PLANK, ANY_PLANK }),

        // 3x3 — iron tools
        new CraftRecipe(new ItemStack(IRON_PICKAXE, 1), 3, 3,
            new Item[][]{ one(IRON_INGOT), one(IRON_INGOT), one(IRON_INGOT), null, S, null, null, S, null }),
        new CraftRecipe(new ItemStack(IRON_SWORD, 1), 3, 3,
            new Item[][]{ null, one(IRON_INGOT), null, null, one(IRON_INGOT), null, null, S, null }),
        new CraftRecipe(new ItemStack(IRON_AXE, 1), 3, 3,
            new Item[][]{ one(IRON_INGOT), one(IRON_INGOT), null, one(IRON_INGOT), S, null, null, S, null }),

        // 3x3 — stone tools
        new CraftRecipe(new ItemStack(STONE_PICKAXE, 1), 3, 3,
            new Item[][]{ ANY_COBBLE, ANY_COBBLE, ANY_COBBLE, null, S, null, null, S, null }),
        new CraftRecipe(new ItemStack(STONE_SWORD, 1), 3, 3,
            new Item[][]{ null, ANY_COBBLE, null, null, ANY_COBBLE, null, null, S, null }),
        new CraftRecipe(new ItemStack(STONE_AXE, 1), 3, 3,
            new Item[][]{ ANY_COBBLE, ANY_COBBLE, null, ANY_COBBLE, S, null, null, S, null }),

        // 3x3 — wooden tools (last resort)
        new CraftRecipe(new ItemStack(WOODEN_PICKAXE, 1), 3, 3,
            new Item[][]{ ANY_PLANK, ANY_PLANK, ANY_PLANK, null, S, null, null, S, null }),
        new CraftRecipe(new ItemStack(WOODEN_SWORD, 1), 3, 3,
            new Item[][]{ null, ANY_PLANK, null, null, ANY_PLANK, null, null, S, null }),
        new CraftRecipe(new ItemStack(WOODEN_AXE, 1), 3, 3,
            new Item[][]{ ANY_PLANK, ANY_PLANK, null, ANY_PLANK, S, null, null, S, null })
    );

    // ── Goal lifecycle ────────────────────────────────────────────────────────

    public FakePlayerCraftGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (entity.level().isClientSide()) return false;
        if (cooldown > 0) { cooldown--; return false; }

        BlockPos nearestTable = findTable(TABLE_SEARCH_RANGE);

        // Place crafting table from inventory if we have one but none nearby
        if (hasItem(CRAFTING_TABLE) && nearestTable == null) {
            pendingRecipe = null;
            state = CraftState.PLACING_TABLE;
            return true;
        }

        // Craft planks from logs if stock is low
        if (countAllPlanks() < 8 && hasAnyLog()) {
            pendingRecipe = null;
            state = CraftState.CRAFTING_2x2;
            return true;
        }

        for (CraftRecipe recipe : RECIPES) {
            Item result = recipe.result().getItem();

            // Crafting table: skip if we already have one or one is nearby
            if (result == CRAFTING_TABLE) {
                if (nearestTable != null || hasItem(CRAFTING_TABLE)) continue;
            } else if (!shouldCraft(recipe)) {
                continue;
            }

            if (!canCraft(recipe)) continue;

            if (!recipe.needsTable()) {
                pendingRecipe = recipe;
                state = CraftState.CRAFTING_2x2;
                return true;
            }

            if (nearestTable != null) {
                pendingRecipe = recipe;
                tablePos = nearestTable;
                state = CraftState.APPROACHING_TABLE;
                return true;
            }
            // 3x3 recipe but no table: wait for crafting table recipe to trigger first
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return !done;
    }

    @Override
    public void start() {
        done = false;
        craftTick = 0;
        if (state == CraftState.APPROACHING_TABLE && tablePos != null) {
            entity.getNavigation().moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        if (cooldown == 0) cooldown = COOLDOWN;
        pendingRecipe = null;
        tablePos = null;
        done = false;
        craftTick = 0;
    }

    @Override
    public void tick() {
        switch (state) {
            case CRAFTING_2x2      -> tickCraft2x2();
            case PLACING_TABLE     -> tickPlaceTable();
            case APPROACHING_TABLE -> tickApproachTable();
            case CRAFTING_3x3      -> tickCraft3x3();
        }
    }

    // ── State ticks ───────────────────────────────────────────────────────────

    private void tickCraft2x2() {
        if (pendingRecipe == null) {
            // Planks from logs
            tryCraftPlanks();
        } else {
            executeCraft(pendingRecipe);
            // After crafting a table, immediately try to place it
            if (pendingRecipe.result().getItem() == CRAFTING_TABLE) {
                state = CraftState.PLACING_TABLE;
                return;
            }
        }
        done = true;
    }

    private void tickPlaceTable() {
        BlockPos spot = findTablePlacementSpot();
        if (spot == null) { done = true; return; }
        entity.level().setBlock(spot, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        consumeItem(CRAFTING_TABLE, 1);
        playPlaceSound(spot, Blocks.CRAFTING_TABLE.defaultBlockState());
        entity.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CRAFTING_TABLE));
        entity.triggerSwingAnim();
        done = true;
    }

    private void tickApproachTable() {
        if (tablePos == null || !entity.level().getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
            done = true;
            return;
        }
        entity.getLookControl().setLookAt(tablePos.getX() + 0.5, tablePos.getY() + 0.5, tablePos.getZ() + 0.5);
        if (entity.blockPosition().distSqr(tablePos) > 6.25) {
            entity.getNavigation().moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 1.0);
        } else {
            entity.getNavigation().stop();
            craftTick = 0;
            state = CraftState.CRAFTING_3x3;
        }
    }

    private void tickCraft3x3() {
        if (tablePos == null || !entity.level().getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
            done = true;
            return;
        }
        entity.getLookControl().setLookAt(tablePos.getX() + 0.5, tablePos.getY() + 0.5, tablePos.getZ() + 0.5);
        craftTick++;
        if (craftTick == 1) entity.triggerSwingAnim();
        if (craftTick >= CRAFT_DURATION) {
            if (pendingRecipe != null) executeCraft(pendingRecipe);
            done = true;
        }
    }

    // ── Craft logic ───────────────────────────────────────────────────────────

    private boolean shouldCraft(CraftRecipe recipe) {
        Item result = recipe.result().getItem();
        if (result == STICK) return countItem(STICK) < 8;
        if (containedIn(result, PICKAXE_TIERS)) return !hasBetterOrEqual(result, PICKAXE_TIERS);
        if (containedIn(result, SWORD_TIERS))   return !hasBetterOrEqual(result, SWORD_TIERS);
        if (containedIn(result, AXE_TIERS))     return !hasBetterOrEqual(result, AXE_TIERS);
        return !hasItem(result);
    }

    private boolean canCraft(CraftRecipe recipe) {
        Map<Item, Integer> needed = resolveIngredients(recipe);
        return needed != null;
    }

    private void executeCraft(CraftRecipe recipe) {
        Map<Item, Integer> needed = resolveIngredients(recipe);
        if (needed == null) return;
        for (Map.Entry<Item, Integer> e : needed.entrySet()) {
            consumeItem(e.getKey(), e.getValue());
        }
        addToInventory(recipe.result().copy());
        entity.setItemInHand(InteractionHand.MAIN_HAND, recipe.result().copy());
        entity.triggerSwingAnim();
    }

    // Returns null if recipe cannot be satisfied; otherwise returns item → count map
    private Map<Item, Integer> resolveIngredients(CraftRecipe recipe) {
        Map<Item, Integer> needed = new HashMap<>();
        for (Item[] slot : recipe.slots()) {
            if (slot == null) continue;
            Item chosen = null;
            for (Item candidate : slot) {
                if (countItem(candidate) > 0) { chosen = candidate; break; }
            }
            if (chosen == null) return null;
            needed.merge(chosen, 1, Integer::sum);
        }
        // Verify total counts are actually available (same item in multiple slots)
        for (Map.Entry<Item, Integer> e : needed.entrySet()) {
            if (countItem(e.getKey()) < e.getValue()) return null;
        }
        return needed;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void tryCraftPlanks() {
        for (int i = 0; i < LOGS.length; i++) {
            if (hasItem(LOGS[i])) {
                consumeItem(LOGS[i], 1);
                addToInventory(new ItemStack(PLANKS_FOR_LOG[i], 4));
                entity.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PLANKS_FOR_LOG[i]));
                entity.triggerSwingAnim();
                return;
            }
        }
    }

    private boolean hasAnyLog() {
        for (Item log : LOGS) if (hasItem(log)) return true;
        return false;
    }

    private int countAllPlanks() {
        int total = 0;
        for (Item plank : ANY_PLANK) total += countItem(plank);
        return total;
    }

    private boolean containedIn(Item item, Item[] array) {
        for (Item i : array) if (i == item) return true;
        return false;
    }

    // Returns true if entity has the item OR a better-tier item
    private boolean hasBetterOrEqual(Item item, Item[] tiers) {
        int itemTier = -1;
        for (int i = 0; i < tiers.length; i++) {
            if (tiers[i] == item) { itemTier = i; break; }
        }
        if (itemTier < 0) return false;
        for (int i = itemTier; i < tiers.length; i++) {
            if (hasItem(tiers[i])) return true;
        }
        return false;
    }

    private BlockPos findTable(int range) {
        BlockPos origin = entity.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!entity.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)) continue;
                    double dist = origin.distSqr(pos);
                    if (dist < bestDist) { bestDist = dist; best = pos.immutable(); }
                }
            }
        }
        return best;
    }

    private BlockPos findTablePlacementSpot() {
        BlockPos feet = entity.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos candidate = feet.offset(dx, 0, dz);
                if (entity.level().getBlockState(candidate).isAir()
                        && entity.level().getBlockState(candidate.below()).isSolid()) {
                    return candidate.immutable();
                }
            }
        }
        return null;
    }

    private boolean hasItem(Item item) {
        return countItem(item) > 0;
    }

    private int countItem(Item item) {
        int total = 0;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    private void consumeItem(Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < entity.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private void addToInventory(ItemStack stack) {
        // Try to merge with existing stacks
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack slot = entity.getInventory().getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItem(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                int add = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(add);
                stack.shrink(add);
                if (stack.isEmpty()) return;
            }
        }
        // Find empty slot
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            if (entity.getInventory().getItem(i).isEmpty()) {
                entity.getInventory().setItem(i, stack.copy());
                return;
            }
        }
        // Inventory full — drop the item
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            entity.spawnAtLocation(serverLevel, stack);
        }
    }

    private void playPlaceSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.world.level.block.SoundType sound = state.getSoundType();
        entity.level().playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
    }
}
