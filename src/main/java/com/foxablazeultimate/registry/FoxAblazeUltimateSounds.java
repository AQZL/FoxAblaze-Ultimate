package com.foxablazeultimate.registry;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 究极模组的 SoundEvent 注册表。
 * <p>与 {@link FoxAblazeUltimateSkills} 同构：{@link #SOUND_EVENTS} 在 {@link #register(IEventBus)} 中挂入 mod bus，
 * 每个 {@link SoundEvent} 的 ResourceLocation 必须与 {@code assets/foxablazeultimate/sounds.json} 的顶层 key 完全一致，
 * 否则客户端会报"Unable to play unknown soundEvent"。
 */
public final class FoxAblazeUltimateSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, FoxAblazeUltimateMod.MOD_ID);

    /**
     * 拉斐尔融合时同步播放的语音（告。个体辅助权限提升……）。
     * <p>文件：{@code assets/foxablazeultimate/sounds/raphael/fusion_declaration.ogg}。
     * 使用 {@link ServerPlayer#playNotifySound} 以"无视距离"的形式仅对目标玩家播放，契合"脑内系统音"的设定。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPHAEL_FUSION_DECLARATION =
            SOUND_EVENTS.register("raphael.fusion_declaration",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    FoxAblazeUltimateMod.MOD_ID, "raphael.fusion_declaration")));

    /**
     * 别西卜融合时同步播放的语音（告。暴食与残虐已被认知为同源贪欲……）。
     * <p>文件：{@code assets/foxablazeultimate/sounds/beelzebub/fusion_declaration.ogg}（用户后续提供，缺失时静默无声）。
     * 同 RAPHAEL_FUSION_DECLARATION：以脑内系统音方式仅对目标玩家播放。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> BEELZEBUB_FUSION_DECLARATION =
            SOUND_EVENTS.register("beelzebub.fusion_declaration",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    FoxAblazeUltimateMod.MOD_ID, "beelzebub.fusion_declaration")));

    /**
     * 誓约之王·乌列尔获得时同步播放的语音（告。无形的誓约已被承认……）。
     * <p>文件：{@code assets/foxablazeultimate/sounds/uriel/skill_declaration.ogg}。
     * <p>不同于 raphael / beelzebub 的"融合仪式"，乌列尔走的是 {@code onLearnSkill} 钩子 —— 即玩家任何途径
     * 第一次学到该技能（包括 /skill add、loot、其他 mod 给予）都会触发宣告，仅对目标玩家本人播放。
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> URIEL_SKILL_DECLARATION =
            SOUND_EVENTS.register("uriel.skill_declaration",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    FoxAblazeUltimateMod.MOD_ID, "uriel.skill_declaration")));

    private FoxAblazeUltimateSounds() {}

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
