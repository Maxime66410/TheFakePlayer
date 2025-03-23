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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true)); // Permet d'attaquer le joueur
        this.goalSelector.addGoal(4, new BreakDoorGoal(this, (HARD) -> {
            return true;
        })); // Permet de casser les portes
        this.goalSelector.addGoal(3, new OpenDoorGoal(this, true)); // Permet d'ouvrir les portes
        this.goalSelector.addGoal(5, new JumpGoal() {
            @Override
            public boolean canUse() {
                return true;
            }
        });
        this.goalSelector.addGoal(5, new PanicGoal(this, 1.25D)); // Permet de paniquer
        this.goalSelector.addGoal(3, new EatBlockGoal(this)); // Permet de manger des blocs
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector
                .addGoal(
                        3,
                        new NearestAttackableTargetGoal<>(
                                this, Mob.class, 5, false, false, (p_28879_, p_363579_) -> p_28879_ instanceof Enemy && !(p_28879_ instanceof Creeper)
                        )
                );
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
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
}
