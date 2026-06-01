package org.furranystudio.thefakeplayer.Entity.Goals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.furranystudio.thefakeplayer.Entity.FakePlayerEntity;

import java.util.EnumSet;

public class FakePlayerChestGoal extends Goal {

    private final FakePlayerEntity entity;
    private BlockPos chestPos = null;
    private int cooldown = 0;
    private int phaseTick = 0;
    private int phase = 0; // 0=approche, 1=attente ouverture, 2=attente fermeture

    private static final int SEARCH_RANGE = 10;
    private static final int OPEN_WAIT = 20;
    private static final int CLOSE_WAIT = 20;
    private static final int COOLDOWN = 300;
    private static final int MIN_FOOD_STOCK = 8;

    public FakePlayerChestGoal(FakePlayerEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        chestPos = findChest();
        return chestPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return chestPos != null && entity.level().getBlockState(chestPos).getBlock() instanceof ChestBlock;
    }

    @Override
    public void start() {
        phase = 0;
        phaseTick = 0;
        entity.getNavigation().moveTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        if (chestPos == null) return;

        entity.getLookControl().setLookAt(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5);

        switch (phase) {
            case 0 -> { // approche
                if (entity.blockPosition().distSqr(chestPos) <= 6.25) {
                    entity.getNavigation().stop();
                    openChest();
                    phase = 1;
                    phaseTick = 0;
                }
            }
            case 1 -> { // attente après ouverture
                if (++phaseTick >= OPEN_WAIT) {
                    interactWithChest();
                    phase = 2;
                    phaseTick = 0;
                }
            }
            case 2 -> { // attente avant fermeture
                if (++phaseTick >= CLOSE_WAIT) {
                    closeChest();
                    cooldown = COOLDOWN;
                    chestPos = null;
                }
            }
        }
    }

    @Override
    public void stop() {
        if (chestPos != null && phase >= 1) closeChest();
        if (cooldown == 0) cooldown = COOLDOWN;
        chestPos = null;
        phase = 0;
        phaseTick = 0;
        entity.getNavigation().stop();
    }

    private void openChest() {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.blockEvent(chestPos, serverLevel.getBlockState(chestPos).getBlock(), 1, 1);
        serverLevel.playSound(null, chestPos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS,
            0.5f, serverLevel.random.nextFloat() * 0.1f + 0.9f);
    }

    private void closeChest() {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.blockEvent(chestPos, serverLevel.getBlockState(chestPos).getBlock(), 1, 0);
        serverLevel.playSound(null, chestPos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS,
            0.5f, serverLevel.random.nextFloat() * 0.1f + 0.9f);
    }

    private void interactWithChest() {
        if (!(entity.level().getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) return;
        takeItems(chest);
        depositItems(chest);
        chest.setChanged();
    }

    private void takeItems(ChestBlockEntity chest) {
        // 1. Nourriture en priorité
        int foodCount = countFoodInInventory();
        if (foodCount < MIN_FOOD_STOCK) {
            for (int i = 0; i < chest.getContainerSize() && foodCount < MIN_FOOD_STOCK; i++) {
                ItemStack stack = chest.getItem(i);
                if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) continue;
                int toTake = Math.min(stack.getCount(), MIN_FOOD_STOCK - foodCount);
                ItemStack taken = chest.removeItem(i, toTake);
                ItemStack leftover = entity.getInventory().addItem(taken);
                if (!leftover.isEmpty()) addToChest(chest, leftover);
                else foodCount += toTake;
            }
        }

        // 2. Outils manquants (catégorie absente de l'inventaire)
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof DiggerItem) && !(stack.getItem() instanceof SwordItem)) continue;
            if (!hasToolCategory(stack)) {
                ItemStack taken = chest.removeItem(i, 1);
                ItemStack leftover = entity.getInventory().addItem(taken);
                if (!leftover.isEmpty()) addToChest(chest, leftover);
            }
        }

        // 3. 1-2 items aléatoires
        int randomTakes = entity.level().random.nextInt(2) + 1;
        int taken = 0;
        for (int i = 0; i < chest.getContainerSize() && taken < randomTakes; i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.isEmpty()) continue;
            int amount = Math.min(stack.getCount(), 8);
            ItemStack split = chest.removeItem(i, amount);
            ItemStack leftover = entity.getInventory().addItem(split);
            if (!leftover.isEmpty()) addToChest(chest, leftover);
            taken++;
        }
    }

    private void depositItems(ChestBlockEntity chest) {
        // Items en excès (> 32 dans un slot)
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (stack.isEmpty() || stack.has(DataComponents.FOOD) || stack.getItem() instanceof DiggerItem) continue;
            if (stack.getCount() > 32) {
                ItemStack deposit = entity.getInventory().removeItem(i, stack.getCount() - 16);
                ItemStack leftover = addToChest(chest, deposit);
                if (!leftover.isEmpty()) entity.getInventory().addItem(leftover);
            }
        }

        // 1-2 items aléatoires non essentiels
        int deposits = entity.level().random.nextInt(2) + 1;
        int done = 0;
        for (int i = 0; i < entity.getInventory().getContainerSize() && done < deposits; i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (stack.isEmpty() || stack.has(DataComponents.FOOD) || stack.getItem() instanceof DiggerItem) continue;
            int amount = Math.min(stack.getCount(), 8);
            ItemStack deposit = entity.getInventory().removeItem(i, amount);
            ItemStack leftover = addToChest(chest, deposit);
            if (!leftover.isEmpty()) entity.getInventory().addItem(leftover);
            done++;
        }
    }

    // Ajoute un stack dans le coffre, retourne le surplus
    private ItemStack addToChest(ChestBlockEntity chest, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < chest.getContainerSize() && !remaining.isEmpty(); i++) {
            ItemStack slot = chest.getItem(i);
            if (slot.isEmpty()) {
                chest.setItem(i, remaining.copy());
                remaining = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slot, remaining)) {
                int space = slot.getMaxStackSize() - slot.getCount();
                int add = Math.min(space, remaining.getCount());
                slot.grow(add);
                remaining.shrink(add);
                chest.setItem(i, slot);
            }
        }
        return remaining;
    }

    private int countFoodInInventory() {
        int count = 0;
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) count += stack.getCount();
        }
        return count;
    }

    // Vérifie si l'inventaire contient déjà un outil de la même catégorie
    private boolean hasToolCategory(ItemStack chestStack) {
        Item item = chestStack.getItem();
        for (int i = 0; i < entity.getInventory().getContainerSize(); i++) {
            Item inv = entity.getInventory().getItem(i).getItem();
            if (item instanceof PickaxeItem && inv instanceof PickaxeItem) return true;
            if (item instanceof AxeItem && inv instanceof AxeItem) return true;
            if (item instanceof SwordItem && inv instanceof SwordItem) return true;
            if (item instanceof ShovelItem && inv instanceof ShovelItem) return true;
            if (item instanceof HoeItem && inv instanceof HoeItem) return true;
        }
        return false;
    }

    private BlockPos findChest() {
        BlockPos origin = entity.blockPosition();
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!(entity.level().getBlockState(pos).getBlock() instanceof ChestBlock)) continue;
                    if (!(entity.level().getBlockEntity(pos) instanceof ChestBlockEntity)) continue;
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
