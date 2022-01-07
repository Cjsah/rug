package net.cjsah.mod.carpet.mixins;

import net.cjsah.mod.carpet.CarpetSettings;
import net.cjsah.mod.carpet.fakes.BlockEntityInterface;
import net.cjsah.mod.carpet.fakes.PistonBlockEntityInterface;
import net.cjsah.mod.carpet.fakes.WorldInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonBlockEntity_movableTEMixin extends BlockEntity implements PistonBlockEntityInterface
{
    @Shadow
    private boolean source;
    @Shadow
    private BlockState pushedBlock;
    
    private BlockEntity carriedBlockEntity;
    private boolean renderCarriedBlockEntity = false;
    private boolean renderSet = false;

    public PistonBlockEntity_movableTEMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }


    /**
     * @author 2No2Name
     */
    public BlockEntity getCarriedBlockEntity()
    {
        return carriedBlockEntity;
    }

    @Override
    public void setLevel(Level world) {
        super.setLevel(world);
        if (carriedBlockEntity != null) carriedBlockEntity.setLevel(world);
    }

    public void setCarriedBlockEntity(BlockEntity blockEntity)
    {
        this.carriedBlockEntity = blockEntity;
        if (this.carriedBlockEntity != null)
        {
            ((BlockEntityInterface)carriedBlockEntity).setCMPos(worldPosition);
            // this might be little dangerous since pos is final for a hashing reason?
            if (level != null) carriedBlockEntity.setLevel(level);
        }
        //    this.carriedBlockEntity.setPos(this.pos);
    }
    
    public boolean isRenderModeSet()
    {
        return renderSet;
    }
    
    public boolean getRenderCarriedBlockEntity()
    {
        return renderCarriedBlockEntity;
    }
    
    public void setRenderCarriedBlockEntity(boolean b)
    {
        renderCarriedBlockEntity = b;
        renderSet = true;
    }
    
    /**
     * @author 2No2Name
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
              target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private static boolean movableTEsetBlockState0(
            Level world, BlockPos blockPos_1, BlockState blockAState_2, int int_1,
            Level world2, BlockPos blockPos, BlockState blockState, PistonMovingBlockEntity pistonBlockEntity)
    {
        if (!CarpetSettings.movableBlockEntities)
            return world.setBlock(blockPos_1, blockAState_2, int_1);
        else
            return ((WorldInterface) (world)).setBlockStateWithBlockEntity(blockPos_1, blockAState_2, ((PistonBlockEntityInterface)pistonBlockEntity).getCarriedBlockEntity(), int_1);
    }
    
    @Redirect(method = "finish", at = @At(value = "INVOKE",
              target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean movableTEsetBlockState1(Level world, BlockPos blockPos_1, BlockState blockState_2, int int_1)
    {
        if (!CarpetSettings.movableBlockEntities)
            return world.setBlock(blockPos_1, blockState_2, int_1);
        else
        {
            boolean ret = ((WorldInterface) (world)).setBlockStateWithBlockEntity(blockPos_1, blockState_2, this.carriedBlockEntity, int_1);
            this.carriedBlockEntity = null; //this will cancel the finishHandleBroken
            return ret;
        }
    }
    
    @Inject(method = "finish", at = @At(value = "RETURN"))
    private void finishHandleBroken(CallbackInfo cir)
    {
        //Handle TNT Explosions or other ways the moving Block is broken
        //Also /setblock will cause this to be called, and drop e.g. a moving chest's contents.
        // This is MC-40380 (BlockEntities that aren't Inventories drop stuff when setblock is called )
        if (CarpetSettings.movableBlockEntities && this.carriedBlockEntity != null && !this.level.isClientSide && this.level.getBlockState(this.worldPosition).getBlock() == Blocks.AIR)
        {
            BlockState blockState_2;
            if (this.source)
                blockState_2 = Blocks.AIR.defaultBlockState();
            else
                blockState_2 = Block.updateFromNeighbourShapes(this.pushedBlock, this.level, this.worldPosition);
            ((WorldInterface) (this.level)).setBlockStateWithBlockEntity(this.worldPosition, blockState_2, this.carriedBlockEntity, 3);
            this.level.destroyBlock(this.worldPosition, false, null);
        }
    }
    
    @Inject(method = "readNbt", at = @At(value = "TAIL"))
    private void onFromTag(CompoundTag NbtCompound_1, CallbackInfo ci)
    {
        if (CarpetSettings.movableBlockEntities && NbtCompound_1.contains("carriedTileEntityCM", 10))
        {
            if (this.pushedBlock.getBlock() instanceof EntityBlock)
                this.carriedBlockEntity = ((EntityBlock) (this.pushedBlock.getBlock())).newBlockEntity(worldPosition, pushedBlock);//   this.world);
            if (carriedBlockEntity != null) //Can actually be null, as BlockPistonMoving.createNewTileEntity(...) returns null
                this.carriedBlockEntity.load(NbtCompound_1.getCompound("carriedTileEntityCM"));
            setCarriedBlockEntity(carriedBlockEntity);
        }
    }
    
    @Inject(method = "writeNbt", at = @At(value = "RETURN", shift = At.Shift.BEFORE))
    private void onToTag(CompoundTag NbtCompound_1, CallbackInfoReturnable<CompoundTag> cir)
    {
        if (CarpetSettings.movableBlockEntities && this.carriedBlockEntity != null)
        {
            //Leave name "carriedTileEntityCM" instead of "carriedBlockEntityCM" for upgrade compatibility with 1.13.2 movable TE
            NbtCompound_1.put("carriedTileEntityCM", this.carriedBlockEntity.saveAdditional(new CompoundTag()));
        }
    }
}
