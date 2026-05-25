package com.foxablazeultimate.ability.skill.beelzebub;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public final class BeelzebubDelegateHelper {

    public static final String GLUTTONY_TAG = "beelzebub_gluttony";

    private BeelzebubDelegateHelper() {}

    public record DelegateResult(boolean activated, int childCooldown) {
        public static final DelegateResult IDLE = new DelegateResult(false, 0);
    }

    public static DelegateResult delegateOnPressed(ManasSkillInstance parent,
                                                    String subTagKey,
                                                    Skill target,
                                                    LivingEntity entity,
                                                    int keyNumber,
                                                    int subMode) {
        TensuraSkillInstance subInstance = readSubInstance(parent, subTagKey, target);
        double subMasteryBefore = subInstance.getMastery();
        target.onPressed(subInstance, entity, keyNumber, subMode);
        int cd = subInstance.getCoolDown(subMode);
        boolean activated = subInstance.onCoolDown(subMode);
        propagateMasteryDelta(parent, entity, subInstance, subMasteryBefore);
        rewireProjectile(parent, subInstance, entity);
        writeBackSubInstance(parent, subTagKey, subInstance);
        return new DelegateResult(activated, cd);
    }

    public static DelegateResult delegateOnHeld(ManasSkillInstance parent,
                                                 String subTagKey,
                                                 Skill target,
                                                 LivingEntity entity,
                                                 int heldTicks,
                                                 int subMode) {
        TensuraSkillInstance subInstance = readSubInstance(parent, subTagKey, target);
        double subMasteryBefore = subInstance.getMastery();
        boolean held = target.onHeld(subInstance, entity, heldTicks, subMode);
        int cd = subInstance.getCoolDown(subMode);
        propagateMasteryDelta(parent, entity, subInstance, subMasteryBefore);
        rewireProjectile(parent, subInstance, entity);
        writeBackSubInstance(parent, subTagKey, subInstance);
        return new DelegateResult(held, cd);
    }

    public static void delegateOnScroll(ManasSkillInstance parent,
                                        String subTagKey,
                                        Skill target,
                                        LivingEntity entity,
                                        double delta,
                                        int subMode) {
        TensuraSkillInstance subInstance = readSubInstance(parent, subTagKey, target);
        target.onScroll(subInstance, entity, delta, subMode);
        writeBackSubInstance(parent, subTagKey, subInstance);
    }

    private static TensuraSkillInstance readSubInstance(ManasSkillInstance parent, String subTagKey, ManasSkill target) {
        CompoundTag parentTag = parent.getOrCreateTag();
        CompoundTag subData = parentTag.contains(subTagKey)
                ? parentTag.getCompound(subTagKey)
                : new CompoundTag();
        TensuraSkillInstance sub = new TensuraSkillInstance(target);
        if (!subData.isEmpty()) {
            sub.deserialize(subData);
        }
        if (sub.getMastery() < parent.getMastery()) {
            sub.setMastery(parent.getMastery());
        }
        return sub;
    }

    private static void writeBackSubInstance(ManasSkillInstance parent, String subTagKey, TensuraSkillInstance sub) {
        parent.getOrCreateTag().put(subTagKey, sub.toNBT());
        parent.markDirty();
    }

    private static void propagateMasteryDelta(ManasSkillInstance parent,
                                              LivingEntity entity,
                                              TensuraSkillInstance subInstance,
                                              double subMasteryBefore) {
        double delta = subInstance.getMastery() - subMasteryBefore;
        if (delta <= 0.0) return;
        parent.getSkill().addMasteryPoint(parent, entity, delta);
    }

    private static void rewireProjectile(ManasSkillInstance parent,
                                         TensuraSkillInstance subInstance,
                                         LivingEntity entity) {
        CompoundTag tag = subInstance.getOrCreateTag();
        if (!tag.contains("Mist")) return;
        int mistId = tag.getInt("Mist");
        if (mistId == 0) return;
        if (entity.level().getEntity(mistId) instanceof TensuraProjectile mist) {
            mist.setSkill(parent);
        }
    }
}
