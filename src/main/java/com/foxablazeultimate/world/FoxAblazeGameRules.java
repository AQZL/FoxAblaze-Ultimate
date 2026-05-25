package com.foxablazeultimate.world;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Category;

/**
 * Fox Ablaze Ultimate 自定义世界规则集合。
 * <p>通过 {@code /gamerule raphaelTrulyUnique [true|false]} 指令在游戏内调整。
 */
public final class FoxAblazeGameRules {

    /**
     * 智慧之王·拉斐尔是否全服唯一（默认 true）。
     * <ul>
     *   <li><b>true</b>：服务器内仅一名玩家可拥有拉斐尔，融合时若已有他人占用则静默阻止。</li>
     *   <li><b>false</b>：解除限制，多名玩家可同时拥有拉斐尔（也不再写入占用记录）。</li>
     * </ul>
     */
    public static GameRules.Key<BooleanValue> RAPHAEL_TRULY_UNIQUE;

    /**
     * 暴食之王·别西卜是否全服唯一（默认 true）。
     * <p>语义同 {@link #RAPHAEL_TRULY_UNIQUE}，但作用对象是别西卜。
     */
    public static GameRules.Key<BooleanValue> BEELZEBUB_TRULY_UNIQUE;

    /**
     * 是否禁止虚数空间捕获实体（默认 false）。
     * <ul>
     *   <li><b>true</b>：开启虚数空间模式后，潜行+右键不再把生物吞入仓库（静默放行，仍走 vanilla 默认交互）。</li>
     *   <li><b>false</b>：保持原行为，捕获按既定规则进行。</li>
     * </ul>
     * 仅影响"实体捕获"环节，不影响仓库本身的物品存取。
     */
    public static GameRules.Key<BooleanValue> BEELZEBUB_DISABLE_CAPTURE;

    /**
     * 是否使本模组的究极技能免疫重置卷（默认 false）。
     * <ul>
     *   <li><b>true</b>：玩家使用任意重置卷（角色重置 / 技能重置 / 种族重置）时，
     *       拉斐尔、别西卜、乌列尔将被静默保留，不会被强制遗忘。</li>
     *   <li><b>false</b>：保持原行为，重置卷可正常移除究极技能。</li>
     * </ul>
     * 实现方式：拦截 ManasCore 的 {@code SkillEvents.REMOVE_SKILL}，对三大究极技能返回中断信号。
     */
    public static GameRules.Key<BooleanValue> PROTECT_ULTIMATE_ON_RESET;

    private FoxAblazeGameRules() {}

    /** 在 mod 构造阶段调用一次；多次调用会重复注册并抛异常。 */
    public static void init() {
        RAPHAEL_TRULY_UNIQUE = GameRules.register(
                "raphaelTrulyUnique", Category.PLAYER, BooleanValue.create(true));
        BEELZEBUB_TRULY_UNIQUE = GameRules.register(
                "beelzebubTrulyUnique", Category.PLAYER, BooleanValue.create(true));
        BEELZEBUB_DISABLE_CAPTURE = GameRules.register(
                "beelzebubDisableCapture", Category.PLAYER, BooleanValue.create(false));
        PROTECT_ULTIMATE_ON_RESET = GameRules.register(
                "protectUltimateOnReset", Category.PLAYER, BooleanValue.create(false));
    }
}
