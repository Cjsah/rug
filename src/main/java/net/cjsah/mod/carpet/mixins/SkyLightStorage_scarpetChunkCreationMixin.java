package net.cjsah.mod.carpet.mixins;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.cjsah.mod.carpet.fakes.ChunkLightProviderInterface;
import net.cjsah.mod.carpet.fakes.LightStorageInterface;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightSectionStorage.BlockDataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;

@Mixin(SkyLightSectionStorage.class)
public abstract class SkyLightStorage_scarpetChunkCreationMixin extends LayerLightSectionStorage<BlockDataLayerStorageMap> implements LightStorageInterface
{
    protected SkyLightStorage_scarpetChunkCreationMixin(final LightLayer lightType, final LightChunkGetter chunkProvider, final BlockDataLayerStorageMap lightData)
    {
        super(lightType, chunkProvider, lightData);
    }

    @Shadow
    @Final
    private LongSet sectionsToUpdate;

    @Shadow
    @Final
    private LongSet sectionsToRemove;

    @Shadow
    @Final
    private LongSet field_15820;

    @Shadow protected abstract boolean isAtOrAboveTopmostSection(final long pos);

    @Shadow protected abstract boolean isSectionEnabled(final long sectionPos);

    @Override
    public void processRemoveLightData(final long cPos)
    {
        for (int y = -1; y < 17; ++y)
        {
            final long sectionPos = SectionPos.asLong(SectionPos.x(cPos), y, SectionPos.z(cPos));

            this.sectionsToUpdate.remove(sectionPos);
            this.sectionsToRemove.remove(sectionPos);

            this.field_15820.remove(sectionPos);
        }
    }

    @Override
    public void processRelight(final LayerLightEngine<?, ?> lightProvider, final long cPos)
    {
        final LevelPropagator_resetChunkInterface levelPropagator = (LevelPropagator_resetChunkInterface) lightProvider;

        for (int y = -1; y < 17; ++y)
        {
            final long sectionPos = SectionPos.asLong(SectionPos.x(cPos), y, SectionPos.z(cPos));
            final long pos = BlockPos.asLong(SectionPos.sectionToBlockCoord(SectionPos.x(sectionPos)), SectionPos.sectionToBlockCoord(y), SectionPos.sectionToBlockCoord(SectionPos.z(sectionPos)));

            if (!this.storingLightForSection(sectionPos))
                continue;

            for (final Direction dir : Direction.Plane.HORIZONTAL)
            {
                long neighborCeilingSectionPos = SectionPos.offset(sectionPos, dir);
                final DataLayer neighborLightArray = this.getDataLayerData(neighborCeilingSectionPos);

                DataLayer neighborCeilingLightArray = neighborLightArray;

                while (neighborCeilingLightArray == null && !this.isAtOrAboveTopmostSection(neighborCeilingSectionPos))
                {
                    neighborCeilingSectionPos = SectionPos.offset(neighborCeilingSectionPos, Direction.UP);
                    neighborCeilingLightArray = this.getDataLayerData(neighborCeilingSectionPos);
                }

                final int ox = 15 * Math.max(dir.getStepX(), 0);
                final int oz = 15 * Math.max(dir.getStepZ(), 0);

                final int dx = Math.abs(dir.getStepZ());
                final int dz = Math.abs(dir.getStepX());

                int emptyLightLevel = (neighborCeilingLightArray == null)
                    ? (this.isSectionEnabled(SectionPos.getZeroNode(neighborCeilingSectionPos)) ? 0 : 15)
                    : 0;
                int neighbourY = (neighborLightArray == null)
                    ? SectionPos.sectionToBlockCoord(SectionPos.y(neighborCeilingSectionPos))
                    : 0;

                for (int t = 0; t < 16; ++t)
                    for (int dy = 0; dy < 16; ++dy)
                    {
                        final long dst = BlockPos.offset(pos, ox + t * dx, dy, oz + t * dz);
                        long src = BlockPos.offset(dst, dir);

                        long adj_src = (neighborLightArray != null)
                            ? src
                            : BlockPos.asLong(BlockPos.getX(src), neighbourY, BlockPos.getZ(src));

                        final int srcLevel = neighborCeilingLightArray != null
                            ? ((ChunkLightProviderInterface) lightProvider).callGetCurrentLevelFromSection(neighborCeilingLightArray, adj_src)
                            : emptyLightLevel;

                        levelPropagator.cmInvokeUpdateLevel(src, dst, levelPropagator.cmCallGetPropagatedLevel(src, dst, srcLevel), true);
                    }
            }
        }
    }
}
