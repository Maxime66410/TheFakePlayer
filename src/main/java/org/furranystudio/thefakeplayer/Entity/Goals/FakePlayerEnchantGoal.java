package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class FakePlayerEnchantGoal extends Goal {

    private final FakePlayerEntity entity;
    private int cooldown = 0;
    private int enchantTick = 0;
    private boolean done = false;

    private BlockPos tablePos = null;
    private ItemStack pendingItem = null;
    private EquipmentSlot pendingEquipSlot = null;
    private int pendingInvIndex = -1;

    private static final int SEARCH_RANGE = 50;
    private static final int ENCHANT_DURATION = 60;
    private static final int COOLDOWN = 600;
    private static final int LAPIS_COST = 3;
    private static final int XP_LEVEL_COST = 3;
    private static final int MAX_ENCHANTS_PER_ITEM = 3;

    // ── Enchantment candidate pools per item type ─────────────────────────────

    @SafeVarargs
    private static ResourceKey<Enchantment>[] pool(ResourceKey<Enchantment>... keys) { return keys; }

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] SWORD_POOL = pool(
        Enchantments.SHARPNESS, Enchantments.FIRE_ASPECT,
        Enchantments.LOOTING, Enchantments.KNOCKBACK
    );

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] PICKAXE_POOL = pool(
        Enchantments.EFFICIENCY, Enchantments.FORTUNE, Enchantments.UNBREAKING
    );

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] AXE_POOL = pool(
        Enchantments.SHARPNESS, Enchantments.EFFICIENCY, Enchantments.UNBREAKING
    );

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] BOW_POOL = pool(
        Enchantments.POWER, Enchantments.FLAME, Enchantments.PUNCH, Enchantments.INFINITY
    );

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] HELMET_POOL = pool(
        Enchantments.PROTECTION, Enchantments.AQUA_AFFINITY, Enchantments.RESPIRATION, Enchantments.UNBREAKING
    );

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] CHEST_LEGS_POOL = pool(
        Enchantments.PROTECTION, Enchantments.UNBREAKING
    );

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Enchantment>[] BOOTS_POOL = pool(
        Enchantments.PROTECTION, Enchantments.FEATHER_FALLING, Enchantments.DEPTH_STRIDER, Enchantments.UNBREAKING
    );

    // ── Goal lifecycle ────────────────────────────────────────────────────────

    public FakePlayerEnchantGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (entity.level().isClientSide()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        if (entity.getStoredXPLevel() < 1) return false;
        if (countItem(Items.LAPIS_LAZULI) < LAPIS_COST) return false;

        pendingItem = findItemToEnchant();
        if (pendingItem == null) return false;

        tablePos = findEnchantingTable();
        return tablePos != null;
    }

    @Override
    public boolean canContinueToUse() { return !done; }

    @Override
    public void start() {
        done = false;
        enchantTick = 0;
        if (tablePos == null) { done = true; return; }
        entity.getNavigation().moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 1.0);
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        if (cooldown == 0) cooldown = COOLDOWN;
        tablePos = null;
        pendingItem = null;
        pendingEquipSlot = null;
        pendingInvIndex = -1;
        done = false;
        enchantTick = 0;
    }

    @Override
    public void tick() {
        if (tablePos == null || !entity.level().getBlockState(tablePos).is(Blocks.ENCHANTING_TABLE)) {
            done = true;
            return;
        }

        entity.getLookControl().setLookAt(tablePos.getX() + 0.5, tablePos.getY() + 0.5, tablePos.getZ() + 0.5);

        if (entity.blockPosition().distSqr(tablePos) > 6.25) {
            entity.getNavigation().moveTo(tablePos.getX() + 0.5, tablePos.getY(), tablePos.getZ() + 0.5, 1.0);
            return;
        }

        entity.getNavigation().stop();
        enchantTick++;
        if (enchantTick == 1) entity.triggerSwingAnim();

        if (enchantTick >= ENCHANT_DURATION) {
            performEnchant();
            done = true;
        }
    }

    // ── Enchant logic ─────────────────────────────────────────────────────────

    private void performEnchant() {
        if (pendingItem == null || pendingItem.isEmpty()) return;
        if (!(entity.level() instanceof ServerLevel sl)) return;

        int xpLevel = entity.getStoredXPLevel();
        int bookshelfPower = countBookshelves(tablePos);
        int enchLevel = Math.min(xpLevel, Math.max(1, bookshelfPower * 2));
        enchLevel = Math.min(enchLevel, 30);

        applyRandomEnchantments(pendingItem, enchLevel, sl);

        // Explicitly put the item back — modifying in-place isn't guaranteed for all slot types
        if (pendingEquipSlot != null) {
            entity.setItemSlot(pendingEquipSlot, pendingItem);
        } else if (pendingInvIndex >= 0) {
            entity.getInventory().setItem(pendingInvIndex, pendingItem);
        }

        consumeLapis(LAPIS_COST);
        entity.consumeXPLevels(XP_LEVEL_COST);
        entity.triggerSwingAnim();
    }

    private void applyRandomEnchantments(ItemStack stack, int enchLevel, ServerLevel sl) {
        ResourceKey<Enchantment>[] candidates = getCandidates(stack);
        if (candidates == null || candidates.length == 0) return;

        HolderLookup.RegistryLookup<Enchantment> reg = sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        List<ResourceKey<Enchantment>> shuffled = new ArrayList<>(List.of(candidates));
        Collections.shuffle(shuffled, new Random(entity.getRandom().nextLong()));

        ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int alreadyHas = existing.entrySet().size();
        int toAdd = Math.max(1, Math.min(enchLevel / 10 + 1, 3)) - alreadyHas;
        if (toAdd <= 0) return;

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
        int added = 0;

        for (ResourceKey<Enchantment> key : shuffled) {
            if (added >= toAdd) break;
            Optional<Holder.Reference<Enchantment>> holderOpt = reg.get(key);
            if (holderOpt.isEmpty()) continue;
            Holder<Enchantment> holder = holderOpt.get();
            // Skip if already has this enchantment
            if (mutable.getLevel(holder) > 0) continue;

            int maxLevel;
            try { maxLevel = holder.value().getMaxLevel(); } catch (Exception e) { maxLevel = 3; }
            int level = Math.max(1, Math.min(maxLevel, enchLevel / 10 + 1));

            mutable.set(holder, level);
            added++;
        }

        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack findItemToEnchant() {
        pendingEquipSlot = null;
        pendingInvIndex = -1;

        // Main hand first (in-place modification works for hand items)
        ItemStack mainHand = entity.getMainHandItem();
        if (shouldEnchant(mainHand)) return mainHand;

        // Equipment slots — track which slot so we can put it back explicitly
        for (EquipmentSlot slot : new EquipmentSlot[]{ EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.HEAD, EquipmentSlot.FEET }) {
            ItemStack s = entity.getItemBySlot(slot);
            if (shouldEnchant(s)) {
                pendingEquipSlot = slot;
                return s;
            }
        }

        // Inventory — track the index so we can put it back explicitly
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack s = entity.getInventory().getItem(i);
            if (shouldEnchant(s)) {
                pendingInvIndex = i;
                return s;
            }
        }

        return null;
    }

    private boolean shouldEnchant(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1) return false;
        if (getCandidates(stack) == null) return false;
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return enchants.entrySet().size() < MAX_ENCHANTS_PER_ITEM;
    }

    private ResourceKey<Enchantment>[] getCandidates(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item instanceof SwordItem)  return SWORD_POOL;
        if (item instanceof PickaxeItem) return PICKAXE_POOL;
        if (item instanceof AxeItem)     return AXE_POOL;
        if (item instanceof BowItem)     return BOW_POOL;
        if (item instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot(stack);
            if (slot == null) return CHEST_LEGS_POOL;
            return switch (slot) {
                case HEAD -> HELMET_POOL;
                case FEET -> BOOTS_POOL;
                default   -> CHEST_LEGS_POOL;
            };
        }
        return null;
    }

    private int countBookshelves(BlockPos pos) {
        int count = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                for (int dy = 0; dy <= 1; dy++) {
                    if (entity.level().getBlockState(pos.offset(dx, dy, dz)).is(Blocks.BOOKSHELF)) {
                        if (++count >= 15) return 15;
                    }
                }
            }
        }
        return count;
    }

    private BlockPos findEnchantingTable() {
        BlockPos origin = entity.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!entity.level().getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) continue;
                    double dist = origin.distSqr(pos);
                    if (dist < bestDist) { bestDist = dist; best = pos.immutable(); }
                }
            }
        }
        return best;
    }

    private int countItem(Item item) {
        int count = 0;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack s = entity.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    private void consumeLapis(int amount) {
        int remaining = amount;
        for (int i = 0; i < entity.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack s = entity.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == Items.LAPIS_LAZULI) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
    }
}
