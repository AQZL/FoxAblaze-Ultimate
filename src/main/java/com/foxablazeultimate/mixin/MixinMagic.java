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

/**
 * 让"智慧之王·拉斐尔"持有者无视精通直接瞬发任意魔法。
 *
 * <p>原版 {@link Magic#isInstantCast(ManasSkillInstance, LivingEntity)} 同时要求
 * {@code MagicUtils.hasChantAnnulment(entity)} 与 {@code instance.isMastered(entity)}，
 * 即使有咏唱无效化技能、若魔法本身未精通也无法瞬发。
 *
 * <p>本注入在方法头部短路：与主模组大贤者 / 觉者 / 分析者完全一致 ——
 * <b>仅当拉斐尔处于 toggled 状态时</b>立即返回 {@code true}，从而被
 * {@code getCastingTime(...)} 视为 1 tick 瞬发，完全跳过咏唱时间与精通门槛。
 * 关掉勾时不生效，与思考加速 / 魔力感知等被动行为一致。
 */
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
