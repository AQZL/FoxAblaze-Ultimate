package com.foxablazeultimate.ability.skill.raphael;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/**
 * 拉斐尔模式委托工具：把 onPressed 委托给原始大贤者 / 变质者，
 * 同时为每个子技能在拉斐尔 NBT 下隔离一块独立 CompoundTag 存储状态。
 */
public final class RaphaelDelegateHelper {

    /** 大贤者状态在 raphael tag 下的键 */
    public static final String SAGE_TAG = "raphael_sage";
    /** 变质者状态在 raphael tag 下的键 */
    public static final String DEGENERATE_TAG = "raphael_degenerate";

    private RaphaelDelegateHelper() {}

    /**
     * 将一次按键委托给目标 {@link Skill}，自动隔离 NBT。
     *
     * @return {@code true} 表示子技能内部设置了冷却（即"能力激活成功"），
     *         {@code false} 表示未设置冷却（即"能力激活失败 / 无有效目标"）。
     */
    public static boolean delegateOnPressed(ManasSkillInstance parent,
                                            String subTagKey,
                                            Skill target,
                                            LivingEntity entity,
                                            int keyNumber,
                                            int subMode) {
        CompoundTag parentTag = parent.getOrCreateTag();
        CompoundTag subData = parentTag.contains(subTagKey)
                ? parentTag.getCompound(subTagKey)
                : new CompoundTag();

        TensuraSkillInstance subInstance = buildSubInstance(target, parent, subData);

        target.onPressed(subInstance, entity, keyNumber, subMode);

        parentTag.put(subTagKey, subInstance.toNBT());
        parent.markDirty();

        return subInstance.onCoolDown(subMode);
    }

    private static TensuraSkillInstance buildSubInstance(ManasSkill target,
                                                          ManasSkillInstance parent,
                                                          CompoundTag subData) {
        TensuraSkillInstance sub = new TensuraSkillInstance(target);
        if (!subData.isEmpty()) {
            sub.deserialize(subData);
        }
        if (sub.getMastery() < parent.getMastery()) {
            sub.setMastery(parent.getMastery());
        }
        return sub;
    }
}
