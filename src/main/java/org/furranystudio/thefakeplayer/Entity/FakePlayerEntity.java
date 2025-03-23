package org.furranystudio.thefakeplayer.Entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class FakePlayerEntity extends Animal implements NeutralMob {

    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private int remainingPersistentAngerTime;
    @javax.annotation.Nullable
    private UUID persistentAngerTarget;

    // Constructeurs
    public FakePlayerEntity(EntityType<? extends Animal> entityType, Level world) {
        super(entityType, world);
    }

    public FakePlayerEntity(EntityType<? extends Animal> entityType, Level world, double x, double y, double z) {
        super(entityType, world);
        this.setPos(x, y, z);
    }

    public FakePlayerEntity(EntityType<? extends Animal> entityType, Level world, BlockPos pos) {
        super(entityType, world);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public FakePlayerEntity(Level world) {
        super(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
    }

    public FakePlayerEntity(Level world, double x, double y, double z) {
        super(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
        this.setPos(x, y, z);
    }

    public FakePlayerEntity(Level world, BlockPos pos) {
        super(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    // Méthodes
    @Override
    protected void registerGoals() {
        // TODO Auto-generated method stub
        this.goalSelector.addGoal(0, new FloatGoal(this)); // Permet de flotter dans l'eau
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1.0D)); // Permet de se déplacer aléatoirement
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F)); // Permet de regarder le joueur
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true)); // Permet d'attaquer le joueur
        this.goalSelector.addGoal(3, new BreakDoorGoal(this, (HARD) -> {
            return true;
        })); // Permet de casser les portes
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true)); // Permet d'ouvrir les portes
        this.goalSelector.addGoal(4, new JumpGoal() {
            @Override
            public boolean canUse() {
                return true;
            }
        });
        this.goalSelector.addGoal(5, new PanicGoal(this, 1.25D)); // Permet de paniquer
        this.goalSelector.addGoal(2, new EatBlockGoal(this)); // Permet de manger des blocs
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector
                .addGoal(
                        2,
                        new NearestAttackableTargetGoal<>(
                                this, Mob.class, 3, false, false, (p_28879_, p_363579_) -> p_28879_ instanceof LivingEntity
                        )
                );
        this.targetSelector.addGoal(6, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    protected void actuallyHurt(ServerLevel p_364204_, DamageSource p_328294_, float p_327706_) {
        super.actuallyHurt(p_364204_, p_328294_, p_327706_);

        Entity attacker = p_328294_.getEntity();
        if (attacker instanceof LivingEntity) {
            this.setTarget((LivingEntity) p_328294_.getEntity());
        }

        // If the entity is dead or dying, remove it
        if (this.isDeadOrDying()) {
            // Give effect slowness and blindness

            if(attacker instanceof LivingEntity) {
                ((LivingEntity) attacker).addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 10));
                ((LivingEntity) attacker).addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
            }

            dropCustomDeathLoot(p_364204_, p_328294_, true);

            this.remove(RemovalReason.DISCARDED);
        }
    }

    public static AttributeSupplier.Builder createFakePlayerAttributes() {
        return Mob.createMobAttributes()
                // Same stats as a player
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.BLOCK_BREAK_SPEED, 5.0D)
                .add(Attributes.MINING_EFFICIENCY, 5.0D)
                .add(Attributes.BLOCK_INTERACTION_RANGE, 6.0D);

    }

    public static boolean canSpawn(EntityType<? extends FakePlayerEntity> p_223365_, LevelAccessor p_223366_, EntitySpawnReason p_223367_, BlockPos p_223368_, RandomSource p_223369_) {
        return Animal.checkAnimalSpawnRules(p_223365_, p_223366_, p_223367_, p_223368_, p_223369_);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel p_146743_, AgeableMob p_146744_) {
        return null;
    }

    @Override
    public boolean isFood(ItemStack p_27600_) {
        return false;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int p_21673_) {
        this.remainingPersistentAngerTime = p_21673_;
    }

    @Override
    public @Nullable UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID p_21672_) {
        this.persistentAngerTarget = p_21672_;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel p_345102_, DamageSource p_21385_, boolean p_21387_) {
        super.dropCustomDeathLoot(p_345102_, p_21385_, p_21387_);
        // Create list of items to drop with random stuff
        List<Item> Lootitems = Arrays.asList(
                Items.DIAMOND,
                Items.GOLD_INGOT,
                Items.IRON_INGOT,
                Items.EMERALD,
                Items.LAPIS_LAZULI,
                Items.REDSTONE,
                Items.COAL,
                Items.QUARTZ,
                Items.NETHERITE_SCRAP,
                Items.NETHERITE_INGOT,
                Items.NETHERITE_SWORD,
                Items.NETHERITE_PICKAXE,
                Items.NETHERITE_AXE,
                Items.NETHERITE_SHOVEL,
                Items.NETHERITE_HOE,
                Items.NETHERITE_HELMET,
                Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_BOOTS,
                Items.DIAMOND_SWORD,
                Items.DIAMOND_PICKAXE,
                Items.DIAMOND_AXE,
                Items.DIAMOND_SHOVEL,
                Items.DIAMOND_HOE,
                Items.DIAMOND_HELMET,
                Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_LEGGINGS,
                Items.DIAMOND_BOOTS,
                Items.GOLDEN_SWORD,
                Items.GOLDEN_PICKAXE,
                Items.GOLDEN_AXE,
                Items.GOLDEN_SHOVEL,
                Items.GOLDEN_HOE,
                Items.GOLDEN_HELMET,
                Items.GOLDEN_CHESTPLATE,
                Items.GOLDEN_LEGGINGS,
                Items.GOLDEN_BOOTS,
                Items.IRON_SWORD,
                Items.IRON_PICKAXE,
                Items.IRON_AXE,
                Items.IRON_SHOVEL,
                Items.IRON_HOE,
                Items.IRON_HELMET,
                Items.IRON_CHESTPLATE,
                Items.IRON_LEGGINGS,
                Items.IRON_BOOTS,
                Items.STONE_SWORD,
                Items.STONE_PICKAXE,
                Items.STONE_AXE,
                Items.STONE_SHOVEL,
                Items.STONE_HOE,
                Items.CHAINMAIL_HELMET,
                Items.CHAINMAIL_CHESTPLATE,
                Items.CHAINMAIL_LEGGINGS,
                Items.CHAINMAIL_BOOTS,
                Items.LEATHER_HELMET,
                Items.LEATHER_CHESTPLATE,
                Items.LEATHER_LEGGINGS,
                Items.LEATHER_BOOTS,
                Items.WOODEN_SWORD,
                Items.WOODEN_PICKAXE,
                Items.WOODEN_AXE,
                Items.WOODEN_SHOVEL,
                Items.WOODEN_HOE,
                Items.BOW,
                Items.ARROW,
                Items.CROSSBOW,
                Items.TIPPED_ARROW,
                Items.SPECTRAL_ARROW,
                Items.SHIELD,
                Items.TRIDENT,
                Items.FISHING_ROD,
                Items.CARROT_ON_A_STICK,
                Items.WARPED_FUNGUS_ON_A_STICK,
                Items.SADDLE,
                Items.LEAD,
                Items.NAME_TAG,
                Items.BOOK,
                Items.ENCHANTED_BOOK,
                Items.WRITTEN_BOOK,
                Items.WRITABLE_BOOK,
                Items.INK_SAC,
                Items.FEATHER,
                Items.LEATHER,
                Items.RABBIT_HIDE,
                Items.STRING,
                Items.BONE,
                Items.ENDER_PEARL,
                Items.ENDER_EYE,
                Items.GHAST_TEAR,
                Items.GUNPOWDER,
                Items.BLAZE_POWDER,
                Items.MAGMA_CREAM,
                Items.SLIME_BALL,
                Items.SPIDER_EYE,
                Items.FERMENTED_SPIDER_EYE,
                Items.PHANTOM_MEMBRANE,
                Items.NETHER_STAR,
                Items.NETHER_WART,
                Items.GLOWSTONE_DUST,
                Items.REDSTONE,
                Items.GLOWSTONE,
                Items.QUARTZ,
                Items.PRISMARINE_SHARD,
                Items.PRISMARINE_CRYSTALS,
                Items.SHULKER_SHELL,
                Items.SHULKER_BOX,
                Items.ELYTRA,
                Items.TOTEM_OF_UNDYING,
                Items.DRAGON_BREATH,
                Items.EXPERIENCE_BOTTLE,
                Items.ENCHANTING_TABLE,
                Items.ANVIL,
                Items.CHIPPED_ANVIL,
                Items.DAMAGED_ANVIL,
                Items.GRINDSTONE,
                Items.SMITHING_TABLE,
                Items.SMOKER,
                Items.BLAST_FURNACE,
                Items.CARTOGRAPHY_TABLE,
                Items.FLETCHING_TABLE,
                Items.SMITHING_TABLE,
                Items.STONECUTTER,
                Items.BREWING_STAND,
                Items.CAULDRON,
                Items.BELL,
                Items.LOOM,
                Items.COMPOSTER,
                Items.BARREL,
                Items.BEEHIVE,
                Items.BEE_NEST,
                Items.CAMPFIRE,
                Items.SOUL_CAMPFIRE,
                Items.LANTERN,
                Items.SOUL_LANTERN,
                Items.TORCH,
                Items.SOUL_TORCH,
                Items.REDSTONE_TORCH,
                Items.REDSTONE_LAMP,
                Items.REDSTONE_BLOCK,
                Items.BEEF,
                Items.PORKCHOP,
                Items.CHICKEN,
                Items.MUTTON,
                Items.RABBIT,
                Items.COD,
                Items.SALMON,
                Items.TROPICAL_FISH,
                Items.PUFFERFISH,
                Items.COOKED_BEEF,
                Items.COOKED_PORKCHOP,
                Items.COOKED_CHICKEN,
                Items.COOKED_MUTTON,
                Items.COOKED_RABBIT,
                Items.COOKED_COD,
                Items.COOKED_SALMON,
                Items.COBBLESTONE,
                Items.STONE,
                Items.GRANITE,
                Items.ACACIA_LOG,
                Items.BIRCH_LOG,
                Items.DARK_OAK_LOG,
                Items.JUNGLE_LOG,
                Items.OAK_LOG,
                Items.SPRUCE_LOG,
                Items.ACACIA_PLANKS,
                Items.BIRCH_PLANKS,
                Items.DARK_OAK_PLANKS,
                Items.JUNGLE_PLANKS,
                Items.OAK_PLANKS,
                Items.SPRUCE_PLANKS,
                Items.ACACIA_SLAB,
                Items.BIRCH_SLAB,
                Items.DARK_OAK_SLAB,
                Items.JUNGLE_SLAB,
                Items.OAK_SLAB,
                Items.SPRUCE_SLAB,
                Items.ACACIA_STAIRS,
                Items.BIRCH_STAIRS,
                Items.DARK_OAK_STAIRS,
                Items.JUNGLE_STAIRS,
                Items.OAK_STAIRS,
                Items.SPRUCE_STAIRS,
                Items.ACACIA_FENCE,
                Items.BIRCH_FENCE,
                Items.DARK_OAK_FENCE,
                Items.JUNGLE_FENCE,
                Items.OAK_FENCE,
                Items.SPRUCE_FENCE,
                Items.ACACIA_FENCE_GATE,
                Items.BIRCH_FENCE_GATE,
                Items.DARK_OAK_FENCE_GATE,
                Items.JUNGLE_FENCE_GATE,
                Items.OAK_FENCE_GATE,
                Items.SPRUCE_FENCE_GATE,
                Items.ACACIA_DOOR,
                Items.BIRCH_DOOR,
                Items.DARK_OAK_DOOR,
                Items.JUNGLE_DOOR,
                Items.OAK_DOOR,
                Items.SPRUCE_DOOR,
                Items.ACACIA_TRAPDOOR,
                Items.BIRCH_TRAPDOOR,
                Items.DARK_OAK_TRAPDOOR,
                Items.JUNGLE_TRAPDOOR,
                Items.OAK_TRAPDOOR,
                Items.SPRUCE_TRAPDOOR
        );

        // Choose number of items to drop between 1 and 32
        int numberOfItems = this.random.nextInt(32) + 1;

        // Now drop the items
        for (int i = 0; i < numberOfItems; i++) {
            boolean isBlock = this.random.nextInt(2) == 0;
            if (isBlock) {
                List<Item> blockItems = Arrays.asList(
                        Items.STONE,
                        Items.OAK_LOG,
                        Items.OAK_PLANKS,
                        Items.COBBLESTONE
                );
                Item blockItem = blockItems.get(this.random.nextInt(blockItems.size()));
                this.spawnAtLocation(new ItemStack(blockItem), p_345102_);
            }
            else
            {
                Item item = Lootitems.get(this.random.nextInt(Lootitems.size()));
                this.spawnAtLocation(new ItemStack(item), p_345102_);
            }
        }
    }

    private void spawnAtLocation(ItemStack itemStack, ServerLevel level) {
        ItemEntity itemEntity = new ItemEntity(level, this.getX(), this.getY(), this.getZ(), itemStack);
        level.addFreshEntity(itemEntity);
    }

}
