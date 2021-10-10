package net.mehvahdjukaar.supplementaries.entities;

import net.mehvahdjukaar.supplementaries.entities.goals.EquipAndRangeAttackGoal;
import net.mehvahdjukaar.supplementaries.entities.goals.ShowWaresGoal;
import net.mehvahdjukaar.supplementaries.inventories.RedMerchantContainer;
import net.mehvahdjukaar.supplementaries.network.NetworkHandler;
import net.mehvahdjukaar.supplementaries.network.SendOrangeTraderOffersPacket;
import net.mehvahdjukaar.supplementaries.setup.ModRegistry;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.item.ExperienceOrbEntity;
import net.minecraft.entity.merchant.villager.AbstractVillagerEntity;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.IPacket;
import net.minecraft.stats.Stats;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.OptionalInt;

//pretty much wantering trader class
public class RedMerchantEntity extends AbstractVillagerEntity implements IRangedAttackMob {
    @Nullable
    private BlockPos wanderTarget;
    private int despawnDelay;

    public int attackCooldown = 0;

    public RedMerchantEntity(EntityType<? extends RedMerchantEntity> type, World world) {
        super(type, world);
        this.forcedLoading = true;
    }

    public RedMerchantEntity(World world) {
        this(ModRegistry.RED_MERCHANT_TYPE.get(), world);
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void registerGoals() {

        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(2, new EquipAndRangeAttackGoal(this, 0.35D, 60, 10, 20, 15, new ItemStack(ModRegistry.BOMB_ITEM.get())));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, MobEntity.class, 8, true, false,
                (mob) ->  (mob instanceof AbstractRaiderEntity || mob instanceof ZombieEntity || mob instanceof ZoglinEntity)));

        this.goalSelector.addGoal(3, new TradeWithPlayerGoal(this));
        this.goalSelector.addGoal(3, new LookAtCustomerGoal(this));

        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, ZombieEntity.class, 6.0F, 0.5D, 0.5D));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, VexEntity.class, 8.0F, 0.5D, 0.5D));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, CreeperEntity.class, 8.0F, 0.5D, 0.5D));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, AbstractRaiderEntity.class, 11.0F, 0.5D, 0.5D));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, ZoglinEntity.class, 8.0F, 0.5D, 0.5D));

        this.goalSelector.addGoal(4, new ShowWaresGoal(this,400, 1600));
        this.goalSelector.addGoal(4, new RedMerchantEntity.MoveToGoal(this, 2.0D, 0.35D));
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.35D));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomWalkingGoal(this, 0.35D));
        //this.goalSelector.addGoal(9, new LookAtWithoutMovingGoal(this, PlayerEntity.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtGoal(this, MobEntity.class, 8.0F));
    }

    @Override
    public void setLastHurtByMob(@Nullable LivingEntity entity) {
        super.setLastHurtByMob(entity);
    }

    @Nullable
    public AgeableEntity getBreedOffspring(ServerWorld world, AgeableEntity entity) {
        return null;
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public ActionResultType mobInteract(PlayerEntity player, Hand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (itemstack.getItem() != Items.VILLAGER_SPAWN_EGG && this.isAlive() && !this.isTrading() && !this.isBaby()) {
            if (hand == Hand.MAIN_HAND) {
                player.awardStat(Stats.TALKED_TO_VILLAGER);
            }

            if (!this.getOffers().isEmpty()) {
                if (!this.level.isClientSide) {
                    this.setTradingPlayer(player);
                    this.openTradingScreen(player, this.getDisplayName(), 1);
                }

            }
            return ActionResultType.sidedSuccess(this.level.isClientSide);
        } else {
            return super.mobInteract(player, hand);
        }
    }


    @Override
    public void updateTrades() {
        MerchantOffers merchantoffers = this.getOffers();
        this.addOffersFromItemListings(merchantoffers, VillagerTradesHandler.TRADES, 7);
    }

    @Override
    public void openTradingScreen(PlayerEntity player, ITextComponent name, int level) {
        OptionalInt optionalint = player.openMenu(new SimpleNamedContainerProvider((i, p, m) -> new RedMerchantContainer(i, p, this), name));
        if (optionalint.isPresent() && player instanceof ServerPlayerEntity) {
            MerchantOffers merchantoffers = this.getOffers();
            if (!merchantoffers.isEmpty()) {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) player),
                        new SendOrangeTraderOffersPacket(optionalint.getAsInt(), merchantoffers, level, this.getVillagerXp(), this.showProgressBar(), this.canRestock())
                );
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundNBT p_213281_1_) {
        super.addAdditionalSaveData(p_213281_1_);
        p_213281_1_.putInt("DespawnDelay", this.despawnDelay);
        if (this.wanderTarget != null) {
            p_213281_1_.put("WanderTarget", NBTUtil.writeBlockPos(this.wanderTarget));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT p_70037_1_) {
        super.readAdditionalSaveData(p_70037_1_);
        if (p_70037_1_.contains("DespawnDelay", 99)) {
            this.despawnDelay = p_70037_1_.getInt("DespawnDelay");
        }

        if (p_70037_1_.contains("WanderTarget")) {
            this.wanderTarget = NBTUtil.readBlockPos(p_70037_1_.getCompound("WanderTarget"));
        }

        this.setAge(Math.max(0, this.getAge()));
    }

    @Override
    public boolean removeWhenFarAway(double p_213397_1_) {
        return false;
    }

    @Override
    protected void rewardTradeXp(MerchantOffer p_213713_1_) {
        if (p_213713_1_.shouldRewardExp()) {
            int i = 3 + this.random.nextInt(4);
            this.level.addFreshEntity(new ExperienceOrbEntity(this.level, this.getX(), this.getY() + 0.5D, this.getZ(), i));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource p_184601_1_) {
        return SoundEvents.WANDERING_TRADER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WANDERING_TRADER_DEATH;
    }

    @Override
    protected SoundEvent getDrinkingSound(ItemStack p_213351_1_) {
        Item item = p_213351_1_.getItem();
        return item == Items.MILK_BUCKET ? SoundEvents.WANDERING_TRADER_DRINK_MILK : SoundEvents.WANDERING_TRADER_DRINK_POTION;
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean p_213721_1_) {
        return p_213721_1_ ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.WANDERING_TRADER_YES;
    }

    public void setDespawnDelay(int p_213728_1_) {
        this.despawnDelay = p_213728_1_;
    }

    public int getDespawnDelay() {
        return this.despawnDelay;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level.isClientSide) {
            if (attackCooldown > 0) attackCooldown--;
            this.maybeDespawn();
        }
    }

    private void maybeDespawn() {
        if (this.despawnDelay > 0 && !this.isTrading() && --this.despawnDelay == 0) {
            this.remove();
        }
    }

    public void setWanderTarget(@Nullable BlockPos p_213726_1_) {
        this.wanderTarget = p_213726_1_;
    }

    @Nullable
    private BlockPos getWanderTarget() {
        return this.wanderTarget;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {

        Vector3d vector3d = target.getDeltaMovement();
        double d0 = target.getX() + vector3d.x - this.getX();
        double d1 = target.getEyeY() - (double) 3.5F - this.getY();
        double d2 = target.getZ() + vector3d.z - this.getZ();
        float f = MathHelper.sqrt(d0 * d0 + d2 * d2);

        BombEntity bomb = new BombEntity(this.level, this, false);
        //bomb.xRot -= -90F;
        bomb.shoot(d0, d1 + (double) (f * 0.24F), d2, 1.25F, 0.9F);

        if (!this.isSilent()) {
            //TODO: sound here
            this.level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITCH_THROW, this.getSoundSource(), 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
        }

        this.level.addFreshEntity(bomb);

    }

    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        amount = super.getDamageAfterMagicAbsorb(source, amount);
        if (source.getEntity() == this) {
            amount = 0.0F;
        }
        //explosion resistant!
        if (source.isExplosion()) {
            amount = (float) ((double) amount * 0.2D);
        }

        return amount;
    }

    class MoveToGoal extends Goal {
        final RedMerchantEntity trader;
        final double stopDistance;
        final double speedModifier;

        MoveToGoal(RedMerchantEntity p_i50459_2_, double p_i50459_3_, double p_i50459_5_) {
            this.trader = p_i50459_2_;
            this.stopDistance = p_i50459_3_;
            this.speedModifier = p_i50459_5_;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public void stop() {
            this.trader.setWanderTarget(null);
            RedMerchantEntity.this.navigation.stop();
        }

        @Override
        public boolean canUse() {
            BlockPos blockpos = this.trader.getWanderTarget();
            return blockpos != null && this.isTooFarAway(blockpos, this.stopDistance);
        }

        @Override
        public void tick() {
            BlockPos blockpos = this.trader.getWanderTarget();
            if (blockpos != null && RedMerchantEntity.this.navigation.isDone()) {
                if (this.isTooFarAway(blockpos, 10.0D)) {
                    Vector3d vector3d = (new Vector3d((double) blockpos.getX() - this.trader.getX(), (double) blockpos.getY() - this.trader.getY(), (double) blockpos.getZ() - this.trader.getZ())).normalize();
                    Vector3d vector3d1 = vector3d.scale(10.0D).add(this.trader.getX(), this.trader.getY(), this.trader.getZ());
                    RedMerchantEntity.this.navigation.moveTo(vector3d1.x, vector3d1.y, vector3d1.z, this.speedModifier);
                } else {
                    RedMerchantEntity.this.navigation.moveTo((double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ(), this.speedModifier);
                }
            }

        }

        private boolean isTooFarAway(BlockPos p_220846_1_, double p_220846_2_) {
            return !p_220846_1_.closerThan(this.trader.position(), p_220846_2_);
        }
    }
}
