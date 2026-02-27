/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import lol.sylvie.sswaystones.block.WaystoneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelChunk.class)
public class LevelChunkMixin {
    @Shadow
    @Final
    Level level;

    @ModifyVariable(method = "setBlockState", at = @At("STORE"), ordinal = 4)
    private boolean sswaystones$fixSetblockUpdating(boolean value, @Local(argsOnly = true) BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return value || (block instanceof WaystoneBlock);
    }
}
