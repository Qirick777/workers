package com.qirick.workers.entity.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * The Citizen: a player-shaped inhabitant of the world.
 *
 * <p>This is deliberately only the skeleton of the entity. It walks, wanders and
 * pathfinds like any other passive mob; the jobs, schedules and interactions
 * that the Citizen is eventually meant to carry come later.
 */
public class CitizenEntity extends PathfinderMob {

    private static final EntityDataAccessor<Byte> DATA_GENDER =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BYTE);

    private static final String TAG_GENDER = "Gender";

    /** Matches the player's eye height so the citizen looks at things from where its eyes are. */
    private static final float EYE_HEIGHT = 1.62F;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
        super(type, level);

        // Citizens live in buildings, so they need to be able to use the doors.
        this.setCanPickUpLoot(false);
        if (this.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
            navigation.setCanPassDoors(true);
        }

        // Safety net for entities that are created without finalizeSpawn ever running
        // (copies, /summon variants, ...). Real spawns re-roll this in finalizeSpawn.
        this.setGender(CitizenGender.random(this.random));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.9D));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    // ------------------------------------------------------------------
    // Gender
    // ------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_GENDER, CitizenGender.MALE.getId());
    }

    public CitizenGender getGender() {
        return CitizenGender.byId(this.entityData.get(DATA_GENDER));
    }

    public void setGender(CitizenGender gender) {
        this.entityData.set(DATA_GENDER, gender.getId());
    }

    // Forge deprecates finalizeSpawn to mark it override-only; overriding it is the intended use.
    @SuppressWarnings("deprecation")
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        this.setGender(CitizenGender.random(level.getRandom()));
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_GENDER, this.getGender().getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_GENDER)) {
            String name = tag.getString(TAG_GENDER);
            for (CitizenGender gender : CitizenGender.values()) {
                if (gender.getSerializedName().equals(name)) {
                    this.setGender(gender);
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return EYE_HEIGHT;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // Citizens are meant to stay where they are put.
        return false;
    }
}
