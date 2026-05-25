package com.foxablazeultimate.mixin;

import java.util.List;

import com.foxablazeultimate.predation.PredationFilterHelper;

import io.github.manasmods.tensura.entity.magic.beam.BeamProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.github.manasmods.tensura.entity.magic.beam.PredatorMistProjectile", remap = false)
public abstract class MixinPredatorMistProjectile extends BeamProjectile {
    private MixinPredatorMistProjectile() {
        super(null, null);
    }

    @Inject(method = "breakBlocks", at = @At("HEAD"), cancellable = true)
    private void foxablazeultimate$skipFilteredBlock(Player player, Vec3 lookAngle, CallbackInfo ci) {
        BlockHitResult result = this.level().clip(new ClipContext(
                player.getEyePosition(),
                player.getEyePosition().add(lookAngle.scale(this.getRange())),
                Block.OUTLINE,
                Fluid.NONE,
                this));
        if (result.getType() != Type.BLOCK) return;
        BlockPos pos = result.getBlockPos();
        if (PredationFilterHelper.isBlockFiltered(player, this.level().getBlockState(pos))) {
            ci.cancel();
        }
    }

    @Redirect(method = {"breakBlocks", "devourTarget"}, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> foxablazeultimate$skipFilteredItemEntities(Level level, Class<T> entityClass, AABB box) {
        List<T> original = level.getEntitiesOfClass(entityClass, box);
        if (!(this.getOwner() instanceof Player player)) return original;
        return original.stream()
                .filter(entity -> !(entity instanceof ItemEntity item)
                        || !PredationFilterHelper.isStackFiltered(player, item.getItem()))
                .toList();
    }
}
