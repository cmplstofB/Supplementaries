package net.mehvahdjukaar.supplementaries.common.block.tiles;

import net.mehvahdjukaar.moonlight.api.block.IBlockHolder;
import net.mehvahdjukaar.moonlight.api.block.ItemDisplayTile;
import net.mehvahdjukaar.moonlight.api.client.model.ExtraModelData;
import net.mehvahdjukaar.moonlight.api.client.model.IExtraModelDataProvider;
import net.mehvahdjukaar.moonlight.api.client.model.ModelDataKey;
import net.mehvahdjukaar.moonlight.api.misc.ForgeOverride;
import net.mehvahdjukaar.moonlight.api.platform.ForgeHelper;
import net.mehvahdjukaar.supplementaries.common.block.ModBlockProperties;
import net.mehvahdjukaar.supplementaries.common.block.blocks.FrameBlock;
import net.mehvahdjukaar.supplementaries.common.block.blocks.ItemShelfBlock;
import net.mehvahdjukaar.supplementaries.common.utils.FlowerPotHandler;
import net.mehvahdjukaar.supplementaries.integration.CompatHandler;
import net.mehvahdjukaar.supplementaries.reg.ModRegistry;
import net.mehvahdjukaar.supplementaries.reg.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class FlowerBoxBlockTile extends ItemDisplayTile implements IBlockHolder, IExtraModelDataProvider {

    public static final ModelDataKey<BlockState> FLOWER_0 = ModBlockProperties.FLOWER_0;
    public static final ModelDataKey<BlockState> FLOWER_1 = ModBlockProperties.FLOWER_1;
    public static final ModelDataKey<BlockState> FLOWER_2 = ModBlockProperties.FLOWER_2;

    private final BlockState[] flowerStates = new BlockState[]{Blocks.AIR.defaultBlockState(),
            Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState()};

    public FlowerBoxBlockTile(BlockPos pos, BlockState state) {
        super(ModRegistry.FLOWER_BOX_TILE.get(), pos, state, 3);
    }

    @Override
    public BlockState getHeldBlock(int index) {
        return flowerStates[index];
    }

    @Override
    public boolean setHeldBlock(BlockState state, int index) {
        if (index >= 0 && index < 3) {
            this.flowerStates[index] = state;

        }
        return false;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        if (this.level instanceof ServerLevel) {
            this.setBlockFromitem(slot, stack.getItem());
            int newLight = Math.max(Math.max(ForgeHelper.getLightEmission(this.getHeldBlock(), level, worldPosition),
                            ForgeHelper.getLightEmission(this.getHeldBlock(1), level, worldPosition)),
                    ForgeHelper.getLightEmission(this.getHeldBlock(2), level, worldPosition));
            this.level.setBlock(this.worldPosition, this.getBlockState().setValue(FrameBlock.LIGHT_LEVEL, newLight), 3);
            //this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ExtraModelData getExtraModelData() {
        return ExtraModelData.builder()
                .with(FLOWER_0, flowerStates[0])
                .with(FLOWER_1, flowerStates[1])
                .with(FLOWER_2, flowerStates[2])
                .build();
    }

    @ForgeOverride
    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition).move(0, 0.25, 0);
    }

    @Override
    public void updateClientVisualsOnLoad() {

        for (int n = 0; n < flowerStates.length; n++) {
            Item item = this.getItem(n).getItem();
            setBlockFromitem(n, item);
        }
        this.requestModelReload();
    }

    private void setBlockFromitem(int n, Item item) {
        Block b = null;
        if (item instanceof BlockItem bi) {
            b = bi.getBlock();
        } else if (CompatHandler.DYNAMICTREES) {
            b = CompatHandler.DynTreesGetOptionalDynamicSapling(item, this.level, this.worldPosition);
        }
        if (b == null) b = Blocks.AIR;
        this.flowerStates[n] = b.defaultBlockState();
    }

    @Override
    public Component getDefaultName() {
        return Component.translatable("block.supplementaries.flower_box");
    }

    public float getYaw() {
        return -this.getDirection().getOpposite().toYRot();
    }

    public Direction getDirection() {
        return this.getBlockState().getValue(ItemShelfBlock.FACING);
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (this.getItem(index).isEmpty()) {
            return (stack.getItem() instanceof BlockItem && stack.is(ModTags.FLOWER_BOX_PLANTABLE)) || FlowerPotHandler.hasSpecialFlowerModel(stack.getItem());
        }
        return false;
    }

    @Override
    public void afterDataPacket(ExtraModelData oldData) {
        IExtraModelDataProvider.super.afterDataPacket(oldData);
    }


    @Override
    public SoundEvent getAddItemSound() {
        return SoundEvents.CROP_PLANTED;
    }
}
