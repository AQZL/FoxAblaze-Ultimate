package com.foxablazeultimate.event;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.world.FoxAblazeGameRules;

import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.Skill.SkillType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class UltimateSkillProtector {

    private UltimateSkillProtector() {}

    public static void init() {
        SkillEvents.REMOVE_SKILL.register((instance, owner, forgetMessage) -> {
            if (instance == null || owner == null) return EventResult.pass();
            Level level = owner.level();
            if (level == null) return EventResult.pass();
            if (!level.getGameRules().getBoolean(FoxAblazeGameRules.PROTECT_ULTIMATE_ON_RESET)) {
                return EventResult.pass();
            }
            if (!isProtectedUltimate(instance.getSkill())) return EventResult.pass();

            return EventResult.interrupt(false);
        });
    }

    private static boolean isProtectedUltimate(ManasSkill skill) {
        if (skill == null) return false;
        ResourceLocation id = skill.getRegistryName();
        if (id == null || !FoxAblazeUltimateMod.MOD_ID.equals(id.getNamespace())) return false;
        if (!(skill instanceof Skill tSkill)) return false;
        return tSkill.getType() == SkillType.ULTIMATE;
    }
}
