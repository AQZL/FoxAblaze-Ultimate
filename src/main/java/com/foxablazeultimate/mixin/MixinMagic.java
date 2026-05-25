package com.foxablazeultimate.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.magic.Magic;
import net.minecraft.world.entity.LivingEntity;

@Mixin(value = Magic.class, remap = false)
public class MixinMagic {

    @Inject(method = "isInstantCast", at = @At("HEAD"), cancellable = true)
    private void foxablazeultimate$raphaelInstantCast(ManasSkillInstance instance,
                                                      LivingEntity entity,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) return;
        if (SkillUtils.isSkillToggled(entity, FoxAblazeUltimateSkills.RAPHAEL.get())) {
            cir.setReturnValue(true);
        }
    }
}
