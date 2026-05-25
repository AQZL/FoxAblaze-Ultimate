package com.foxablazeultimate.ability.skill.raphael;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public final class RaphaelDelegateHelper {

    public static final String SAGE_TAG = "raphael_sage";
    public static final String DEGENERATE_TAG = "raphael_degenerate";

    private RaphaelDelegateHelper() {}

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
