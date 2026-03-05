package com.schematicsplus.mixin;

import com.schematicsplus.schematic.PlacementManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts ClientWorld.setBlockState to prevent the server from
 * restoring blocks that the player intentionally broke inside a
 * confirmed schematic region.
 *
 * When the server tries to put air back where the player broke a block
 * that was already removed from the schematic's relativeBlocks map,
 * we just let it through. But when the server tries to restore a block
 * at a position that is NO LONGER in the schematic (because the player
 * broke it and we called removeBlockAt), we cancel the restore.
 */
@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSetBlockState(BlockPos pos, BlockState state, int flags,
                                 CallbackInfoReturnable<Boolean> cir) {
        PlacementManager pm = PlacementManager.getInstance();
        if (!pm.hasPreview()) return;

        // Only intercept when confirmed (locked in place)
        if (!pm.isConfirmed()) return;

        // Check if this position was intentionally removed from the schematic
        // (i.e. player broke it — it's no longer in relativeBlocks)
        if (pm.wasManuallyBroken(pos)) {
            // Cancel the server's attempt to restore the block
            cir.setReturnValue(false);
        }
    }
}