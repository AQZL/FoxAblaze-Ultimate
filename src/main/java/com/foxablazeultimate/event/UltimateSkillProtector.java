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

/**
 * 究极技能 · 重置卷免疫保护器。
 *
 * <h3>触发场景</h3>
 * <p>Tensura 的所有重置卷（角色重置 / 技能重置 / 种族重置）在移除某个技能前，
 * 会先触发 ManasCore 的 {@link SkillEvents#REMOVE_SKILL} 事件；只要事件返回结果是
 * "interrupted false"（{@code .isFalse() == true}），重置卷就会跳过这个技能继续处理下一个，
 * 该技能的 NBT 数据、精通度、CD、模式都会原封不动保留。
 *
 * <h3>本类策略</h3>
 * <p>仅当世界规则 {@link FoxAblazeGameRules#PROTECT_ULTIMATE_ON_RESET} 为 true，
 * 且被移除的 {@link ManasSkill} 同时满足以下两个条件时，才返回
 * {@link EventResult#interrupt(Boolean) interrupt(false)} 阻断移除：
 * <ul>
 *   <li>注册命名空间为 {@link FoxAblazeUltimateMod#MOD_ID}（本模组注册的技能）；</li>
 *   <li>是 Tensura 体系下的 {@link Skill}，且类型为 {@link SkillType#ULTIMATE}。</li>
 * </ul>
 * <p>其他情形一律 {@code pass}，不影响普通技能的正常遗忘 / 重置流程。
 *
 * <h3>未来扩展</h3>
 * <p>本判定基于 namespace + SkillType 动态完成，<b>之后新增的任何究极技能</b>只要满足
 * "在本模组内注册 + 继承 Skill 且 SkillType.ULTIMATE"，自动享有同一份保护，无需修改本类。
 *
 * <h3>注意</h3>
 * <ul>
 *   <li>仅服务端会触发重置卷流程，所以这里不需要客户端分支。</li>
 *   <li>本拦截只针对"完整技能实例移除"事件，与精通度衰减、CD 调整等其他流程无关。</li>
 * </ul>
 */
public final class UltimateSkillProtector {

    private UltimateSkillProtector() {}

    /** 在 mod 构造阶段调用一次。重复调用会注册多个监听器，应避免。 */
    public static void init() {
        SkillEvents.REMOVE_SKILL.register((instance, owner, forgetMessage) -> {
            if (instance == null || owner == null) return EventResult.pass();
            Level level = owner.level();
            if (level == null) return EventResult.pass();
            if (!level.getGameRules().getBoolean(FoxAblazeGameRules.PROTECT_ULTIMATE_ON_RESET)) {
                return EventResult.pass();
            }
            if (!isProtectedUltimate(instance.getSkill())) return EventResult.pass();
            // 阻断移除：重置卷会因 .isFalse() == true 跳过该技能，技能数据保留。
            return EventResult.interrupt(false);
        });
    }

    /**
     * 判断给定 ManasSkill 是否为本模组的究极技能。
     * <p>条件：注册命名空间 == {@code foxablazeultimate} 且 SkillType == ULTIMATE。
     */
    private static boolean isProtectedUltimate(ManasSkill skill) {
        if (skill == null) return false;
        ResourceLocation id = skill.getRegistryName();
        if (id == null || !FoxAblazeUltimateMod.MOD_ID.equals(id.getNamespace())) return false;
        if (!(skill instanceof Skill tSkill)) return false;
        return tSkill.getType() == SkillType.ULTIMATE;
    }
}
