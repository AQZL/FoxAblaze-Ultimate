package com.foxablazeultimate.ability.skill.beelzebub;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/**
 * 别西卜模式委托工具：把 onPressed / onHeld / onScroll 委托给原始暴食者（Gluttony），
 * 同时为子技能在别西卜 NBT 下隔离一块独立 CompoundTag。
 *
 * <p>设计同 {@code RaphaelDelegateHelper}：父技能的 mastery 会被同步到子实例，子实例运行后再写回，
 * 这样原版的所有 NBT 字段（{@code blockMode} / {@code range} / {@code predationList} / {@code storedMP} 等）
 * 都不会污染父 instance 的根 tag，互不冲突。
 *
 * <p><b>残虐者已废弃</b>：mode 7/8 经用户决议精简删除，融合后残虐者本体也被遗忘。
 */
public final class BeelzebubDelegateHelper {

    /** 暴食者状态在 beelzebub tag 下的键。 */
    public static final String GLUTTONY_TAG = "beelzebub_gluttony";

    private BeelzebubDelegateHelper() {}

    /** 委托结果。{@code activated} 反映子技能是否进入冷却（成功语义）；{@code childCooldown} 用于让 parent 也走对应冷却。 */
    public record DelegateResult(boolean activated, int childCooldown) {
        public static final DelegateResult IDLE = new DelegateResult(false, 0);
    }

    /**
     * 委托一次 onPressed。
     *
     * @return {@link DelegateResult}，含 child instance 是否进入冷却 + 写入的冷却值（tick 数）。
     */
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

    /** 委托一次 onHeld。返回 target.onHeld 的结果（heldTicks 是否继续）；child cd 同样会回传。 */
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

    /** 委托一次 onScroll。 */
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

    /** 读取（必要时新建）一个与 parent mastery 同步的子 instance。 */
    private static TensuraSkillInstance readSubInstance(ManasSkillInstance parent, String subTagKey, ManasSkill target) {
        CompoundTag parentTag = parent.getOrCreateTag();
        CompoundTag subData = parentTag.contains(subTagKey)
                ? parentTag.getCompound(subTagKey)
                : new CompoundTag();
        TensuraSkillInstance sub = new TensuraSkillInstance(target);
        if (!subData.isEmpty()) {
            sub.deserialize(subData);
        }
        // mastery 联动：父 mastery > 子 mastery 时，把子 instance mastery 抬到父值
        // 这样残虐者 / 暴食者会在 onDamageEntity / onPressed 中拿到正确的 isMastered() 结果
        if (sub.getMastery() < parent.getMastery()) {
            sub.setMastery(parent.getMastery());
        }
        return sub;
    }

    /** 把子 instance 的 NBT 落回父 tag。 */
    private static void writeBackSubInstance(ManasSkillInstance parent, String subTagKey, TensuraSkillInstance sub) {
        parent.getOrCreateTag().put(subTagKey, sub.toNBT());
        parent.markDirty();
    }

    /**
     * Bug fix（mastery 归属）：
     * 子 instance 在 {@code GluttonySkill.onHeld / onPressed} 内可能被加 mastery（捕食长按、腐蚀长按、隔绝吞物品等）；
     * 这部分增量必须同步给 <b>父 Beelzebub</b>，否则玩家用别西卜锻炼出的熟练度永远写在 sub-tag 里，
     * 表现为「用别西卜，暴食者却涨经验」（实际上是 sub-instance Gluttony NBT 涨经验，玩家本体 Beelzebub 不动）。
     *
     * <p>读取增量后调用 {@code parent.getSkill().addMasteryPoint(parent, entity, delta)} 让父走一次正常的
     * mastery 加成（包含 SkillEvents.SKILL_MASTERY 事件、onSkillMastered 等回调），与玩家直接练 Beelzebub 一致。
     */
    private static void propagateMasteryDelta(ManasSkillInstance parent,
                                              LivingEntity entity,
                                              TensuraSkillInstance subInstance,
                                              double subMasteryBefore) {
        double delta = subInstance.getMastery() - subMasteryBefore;
        if (delta <= 0.0) return;
        parent.getSkill().addMasteryPoint(parent, entity, delta);
    }

    /**
     * Bug fix（捕食弹幕归属）：
     * {@link io.github.manasmods.tensura.ability.skill.unique.GluttonySkill#onHeld} 在 mode 0（捕食）里调
     * {@code PredatorMistProjectile.spawnPredationMist(...)}，把 sub-instance 作为 {@code mist.setSkill(...)} 传入。
     * 弹幕之后所有 SkillAPI 反查（{@code addItemToSpatialStorage / devourEP / saveMagiculeIntoStorage}）都通过
     * {@code SkillAPI.getSkillsFrom(owner).getSkill(this.getSkill().getSkill())} —— 即查 GluttonySkill.class，
     * 命中玩家本体的「实际暴食者实例」（如果存在）或返回空（融合后正常情况），
     * <b>结果是吞下的物品 / EP / predationList 全部进了暴食者胃袋而非别西卜虚数空间</b>。
     *
     * <p>这里在 {@code spawnPredationMist} 写入 sub.tag {@code "Mist"} 字段（=mist 实体 id）后立即把弹幕的
     * skill 引用换回父，使得弹幕之后所有 SkillAPI 反查命中 BeelzebubSkill.class，物品流入别西卜虚数空间。
     *
     * <p>对其他模式无副作用：非捕食模式不会写 {@code "Mist"} 字段，方法直接 noop 返回。
     */
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
