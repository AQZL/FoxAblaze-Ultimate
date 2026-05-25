package com.foxablazeultimate.registry;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.ability.skill.beelzebub.BeelzebubSkill;
import com.foxablazeultimate.ability.skill.raphael.RaphaelSkill;
import com.foxablazeultimate.ability.skill.uriel.UrielSkill;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FoxAblazeUltimateSkills {

    public static final DeferredRegister<ManasSkill> SKILL_REGISTRY =
            DeferredRegister.create(SkillAPI.getSkillRegistryKey(), FoxAblazeUltimateMod.MOD_ID);


    public static final DeferredHolder<ManasSkill, RaphaelSkill> RAPHAEL =
            SKILL_REGISTRY.register("raphael", RaphaelSkill::new);

    public static final DeferredHolder<ManasSkill, BeelzebubSkill> BEELZEBUB =
            SKILL_REGISTRY.register("beelzebub", BeelzebubSkill::new);

    public static final DeferredHolder<ManasSkill, UrielSkill> URIEL =
            SKILL_REGISTRY.register("uriel", UrielSkill::new);

    private FoxAblazeUltimateSkills() {}

    public static void register(IEventBus modBus) {
        SKILL_REGISTRY.register(modBus);
    }
}
