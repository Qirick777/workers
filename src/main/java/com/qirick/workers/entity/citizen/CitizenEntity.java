package com.qirick.workers.entity.citizen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
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
public class CitizenEntity extends PathfinderMob implements InventoryCarrier {

    private static final EntityDataAccessor<Byte> DATA_GENDER =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BYTE);

    private static final String TAG_GENDER = "Gender";

    /** Same size as a villager's inventory. */
    private static final int INVENTORY_SIZE = 8;

    /** Matches the player's eye height so the citizen looks at things from where its eyes are. */
    private static final float EYE_HEIGHT = 1.62F;

    /**
     * Any drop chance above 1.0 means "always", and vanilla reads it twice: it drops the
     * item however the citizen died rather than only for player kills, and it hands the
     * item over undamaged instead of rolling durability off it.
     */
    private static final float ALWAYS_DROP = 2.0F;

    private static final String TAG_MINING = "Mining";
    private static final String TAG_DEPTH = "MineDepth";
    private static final String TAG_HOME = "ReturnPoint";

    /** Where a shift heads when no depth is given: iron, coal and copper all reach here. */
    public static final int DEFAULT_DEPTH = 40;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);

    private boolean mining;
    private int mineDepth = DEFAULT_DEPTH;
    @Nullable
    private net.minecraft.core.BlockPos returnPoint;

    @Nullable
    private com.qirick.workers.entity.citizen.ai.MinerGoal minerGoal;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
        super(type, level);

        this.setCanPickUpLoot(true);

        this.alwaysDropEquipment();

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

    /**
     * Walking, wandering and idling only. Reactions to being hurt, to other mobs or to
     * the world belong to later steps, once it is decided what a Citizen should do.
     *
     * <p>{@link FloatGoal} is here because without it the entity sinks and drowns rather
     * than behaving like a normal mob in water.
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.minerGoal = new com.qirick.workers.entity.citizen.ai.MinerGoal(this);
        this.goalSelector.addGoal(1, this.minerGoal);
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.6D) {
            @Override
            public boolean canUse() {
                return !CitizenEntity.this.isMining() && super.canUse();
            }
        });
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------
    // Mining
    // ------------------------------------------------------------------

    public boolean isMining() {
        return this.mining;
    }

    public void setMining(boolean mining) {
        this.mining = mining;
    }

    public int getMineDepth() {
        return this.mineDepth;
    }

    public void setMineDepth(int depth) {
        this.mineDepth = depth;
    }

    @Nullable
    public net.minecraft.core.BlockPos getReturnPoint() {
        return this.returnPoint;
    }

    public void setReturnPoint(@Nullable net.minecraft.core.BlockPos pos) {
        this.returnPoint = pos == null ? null : pos.immutable();
    }

    public String mineStatus() {
        if (!this.mining) {
            return "idle";
        }
        return this.minerGoal == null ? "mining" : this.minerGoal.describe();
    }

    public String mineName() {
        return "Citizen#" + this.getId();
    }

    // ------------------------------------------------------------------
    // Inventory
    // ------------------------------------------------------------------

    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    /**
     * Stores what the citizen walks into, the way a villager does.
     *
     * <p>Reaching for an item on purpose is a separate behaviour and is not here yet:
     * {@code Mob.aiStep} only offers items the citizen is already standing next to.
     * What ends up in the inventory stays there - moving it into the hand is for the
     * work logic to decide later.
     */
    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        InventoryCarrier.pickUpItem(this, this, itemEntity);
    }

    /**
     * A citizen carries what it owns rather than wearing loot, so nothing it holds
     * evaporates on death.
     *
     * <p>Vanilla persists drop chances per entity, so this is reapplied on load as well
     * as set on creation - otherwise a citizen from an earlier save would keep the old
     * chances and quietly swallow its armour.
     */
    private void alwaysDropEquipment() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, ALWAYS_DROP);
        }
    }

    /**
     * Everything the citizen was carrying is left behind: {@code super} empties the
     * equipment slots, which always drop, and the stored inventory follows it onto
     * the ground.
     */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        this.getInventory().removeAllItems().forEach(this::spawnAtLocation);
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
        tag.putBoolean(TAG_MINING, this.mining);
        tag.putInt(TAG_DEPTH, this.mineDepth);
        if (this.returnPoint != null) {
            tag.putIntArray(TAG_HOME, new int[]{
                    this.returnPoint.getX(), this.returnPoint.getY(), this.returnPoint.getZ()});
        }
        this.writeInventoryToTag(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.mining = tag.getBoolean(TAG_MINING);
        this.mineDepth = tag.contains(TAG_DEPTH) ? tag.getInt(TAG_DEPTH) : DEFAULT_DEPTH;
        if (tag.contains(TAG_HOME)) {
            int[] h = tag.getIntArray(TAG_HOME);
            if (h.length == 3) {
                this.returnPoint = new net.minecraft.core.BlockPos(h[0], h[1], h[2]);
            }
        }
        this.alwaysDropEquipment();
        this.readInventoryFromTag(tag);
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
    protected SoundEvent getHurtSound(DamageSource source) {
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
