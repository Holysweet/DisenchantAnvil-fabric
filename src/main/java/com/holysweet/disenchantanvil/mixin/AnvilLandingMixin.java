package com.holysweet.disenchantanvil.mixin;

import com.holysweet.disenchantanvil.config.DAConfig;
import com.holysweet.disenchantanvil.logic.AnvilProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallingBlockEntity.class)
public abstract class AnvilLandingMixin {

    @Shadow
    public abstract BlockState getBlockState();

    @Unique
    private boolean disenchantanvil$processedLanding = false;

    @Inject(
            method = "causeFallDamage",
            at = @At("HEAD")
    )
    private void disenchantanvil$beforeFallImpact(
            double fallDistance,
            float damageMultiplier,
            DamageSource damageSource,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (disenchantanvil$processedLanding) {
            return;
        }

        FallingBlockEntity fallingBlock = (FallingBlockEntity) (Object) this;
        Level level = fallingBlock.level();

        if (level.isClientSide()) {
            return;
        }

        BlockState fallingState = getBlockState();

        if (!(fallingState.getBlock() instanceof AnvilBlock)) {
            return;
        }

        BlockPos anvilPos = fallingBlock.blockPosition();
        BlockState baseState = level.getBlockState(anvilPos.below());

        if (!DAConfig.isAllowedBase(baseState.getBlock())) {
            return;
        }

        disenchantanvil$processedLanding = true;
        AnvilProcessor.processLanding(level, anvilPos);
    }
}