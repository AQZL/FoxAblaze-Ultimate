package com.foxablazeultimate.mixin.stextras;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.client.ClientCrystalLockState;

import net.minecraft.resources.ResourceLocation;

@Mixin(targets = "org.crypticdev.stextras.storage.STExtarsStorage$Player",
       priority = 500, remap = false)
public class STExtrasIsSkillValidUnlockRaphaelMixin {

    @Inject(method = "isSkillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private static void foxablazeultimate$allowRaphaelWhenCrystalLocked(
            ResourceLocation skill, CallbackInfoReturnable<Boolean> cir) {
        if (skill == null) return;
        if (!FoxAblazeUltimateMod.MOD_ID.equals(skill.getNamespace())) return;
        if (!"raphael".equals(skill.getPath())) return;
        if (!ClientCrystalLockState.isRaphaelCrystalLocked()) return;
        cir.setReturnValue(true);
    }
}
