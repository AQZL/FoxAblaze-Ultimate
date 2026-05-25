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

/**
 * 究极技能注册表。使用 DeferredRegister 向 ManasCore 技能注册表注册。
 */
public final class FoxAblazeUltimateSkills {

    public static final DeferredRegister<ManasSkill> SKILL_REGISTRY =
            DeferredRegister.create(SkillAPI.getSkillRegistryKey(), FoxAblazeUltimateMod.MOD_ID);

    // =====================
    // | Ultimate Skills   |
    // =====================

    /** 智慧之王·拉斐尔 —— 由大贤者与变质者融合而成的究极技能（详见 {@link RaphaelSkill}）。 */
    public static final DeferredHolder<ManasSkill, RaphaelSkill> RAPHAEL =
            SKILL_REGISTRY.register("raphael", RaphaelSkill::new);

    /** 暴食之王·别西卜 —— 由暴食者与残虐者融合而成的究极技能（详见 {@link BeelzebubSkill}）。 */
    public static final DeferredHolder<ManasSkill, BeelzebubSkill> BEELZEBUB =
            SKILL_REGISTRY.register("beelzebub", BeelzebubSkill::new);

    /** 誓约之王·乌列尔 —— 由智慧之王·拉斐尔统合「无限牢狱（精通）」而进化得到（占位骨架，详见 {@link UrielSkill}）。 */
    public static final DeferredHolder<ManasSkill, UrielSkill> URIEL =
            SKILL_REGISTRY.register("uriel", UrielSkill::new);

    private FoxAblazeUltimateSkills() {}

    public static void register(IEventBus modBus) {
        SKILL_REGISTRY.register(modBus);
    }
}
