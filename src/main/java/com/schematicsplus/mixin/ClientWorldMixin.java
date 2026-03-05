package com.schematicsplus.mixin;

import com.schematicsplus.schematic.PlacementManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                 CallbackInfoReturnable<Boolean> cir) {
        PlacementManager pm = PlacementManager.getInstance();
        if (!pm.hasPreview()) return;
        if (!pm.isConfirmed()) return;
        if (pm.wasManuallyBroken(pos)) {
            cir.setReturnValue(false);
        }
    }
}