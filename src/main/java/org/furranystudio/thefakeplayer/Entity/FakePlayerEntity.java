package org.furranystudio.thefakeplayer.Entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class FakePlayerEntity extends Animal {

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
        this(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
    }

    public FakePlayerEntity(Level world, double x, double y, double z) {
        this(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
        this.setPos(x, y, z);
    }

    public FakePlayerEntity(Level world, BlockPos pos) {
        this(ModEntities.FAKE_PLAYER_ENTITY.get(), world);
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    // Méthodes
    @Override
    protected void registerGoals() {
        // TODO Auto-generated method stub
        this.goalSelector.addGoal(0, new FloatGoal(this)); // Permet de flotter dans l'eau
        this.goalSelector.addGoal(1, new RandomStrollGoal(this, 1.0D)); // Permet de se déplacer aléatoirement
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F)); // Permet de regarder le joueur
    }

    /*public ResourceLocation getSkinTexture() {
        UUID fakeUUID = this.getUUID(); // Génère un UUID unique
        return DefaultPlayerSkin.getDefaultSkin(fakeUUID); // Utilise un skin vanilla aléatoire
    }*/

    public static AttributeSupplier.Builder createFakePlayerAttributes() {
        return Player.createAttributes();
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
}
