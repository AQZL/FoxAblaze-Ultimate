package com.foxablazeultimate;

import org.slf4j.Logger;

import com.foxablazeultimate.config.BeelzebubConfig;
import com.foxablazeultimate.config.RaphaelConfig;
import com.foxablazeultimate.config.UrielConfig;
import com.foxablazeultimate.event.UltimateSkillProtector;
import com.foxablazeultimate.network.OpenBeelzebubStoragePayload;
import com.foxablazeultimate.network.OpenRaphaelNamingPayload;
import com.foxablazeultimate.network.RequestBeelzebubFluidExtractPayload;
import com.foxablazeultimate.network.RequestPredationFilterSyncPayload;
import com.foxablazeultimate.network.RequestRaphaelRenamePayload;
import com.foxablazeultimate.network.SyncPredationFilterPayload;
import com.foxablazeultimate.network.UpdatePredationFilterPayload;
import com.foxablazeultimate.registry.FoxAblazeUltimateDataComponents;
import com.foxablazeultimate.registry.FoxAblazeUltimateItems;
import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;
import com.foxablazeultimate.registry.FoxAblazeUltimateSounds;
import com.foxablazeultimate.world.FoxAblazeGameRules;
import com.mojang.logging.LogUtils;

import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.NetworkUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Fox Ablaze Ultimate —— 为 Tensura 添加更多究极技能的扩展模组。
 * <p>作者：AblazeAQZL ・ 官网：https://ablazeaqzl.cn
 */
@Mod(FoxAblazeUltimateMod.MOD_ID)
public class FoxAblazeUltimateMod {

    public static final String MOD_ID = "foxablazeultimate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FoxAblazeUltimateMod(IEventBus modBus, ModContainer modContainer) {
        // 配置必须先于 SkillRegistry 加载，确保技能 static 块取到的是最新值
        RaphaelConfig raphaelCfg = new RaphaelConfig();
        RaphaelConfig.setInstance(raphaelCfg);
        ConfigRegistry.registerConfig(raphaelCfg);

        BeelzebubConfig beelzebubCfg = new BeelzebubConfig();
        BeelzebubConfig.setInstance(beelzebubCfg);
        ConfigRegistry.registerConfig(beelzebubCfg);

        UrielConfig urielCfg = new UrielConfig();
        UrielConfig.setInstance(urielCfg);
        ConfigRegistry.registerConfig(urielCfg);

        // 自定义世界规则注册（mod 构造阶段是合法时机）
        FoxAblazeGameRules.init();

        // 究极技能 · 重置卷免疫保护：监听 SkillEvents.REMOVE_SKILL，按 gamerule 拦截。
        UltimateSkillProtector.init();

        FoxAblazeUltimateSkills.register(modBus);
        FoxAblazeUltimateSounds.register(modBus);
        FoxAblazeUltimateItems.register(modBus);
        FoxAblazeUltimateDataComponents.register(modBus);

        // S2C：打开别西卜虚数胃袋 GUI。注册时 ManasCore 的 NetworkUtils 已经处理了 client/server 分支。
        NetworkUtils.registerS2CPayload(
                OpenBeelzebubStoragePayload.TYPE,
                OpenBeelzebubStoragePayload.STREAM_CODEC,
                OpenBeelzebubStoragePayload::handle);

        // C2S：玩家在虚数空间 GUI 里光标拿物品右键水/熔岩条，请求服务端抽取液体。
        NetworkUtils.registerC2SPayload(
                RequestBeelzebubFluidExtractPayload.TYPE,
                RequestBeelzebubFluidExtractPayload.STREAM_CODEC,
                RequestBeelzebubFluidExtractPayload::handle);

        // S2C：服务端推送当前拉斐尔捕食过滤器的全量状态。
        NetworkUtils.registerS2CPayload(
                SyncPredationFilterPayload.TYPE,
                SyncPredationFilterPayload.STREAM_CODEC,
                SyncPredationFilterPayload::handle);

        // C2S：客户端在暴食 / 别西卜 GUI 打开时请求过滤器同步。
        NetworkUtils.registerC2SPayload(
                RequestPredationFilterSyncPayload.TYPE,
                RequestPredationFilterSyncPayload.STREAM_CODEC,
                RequestPredationFilterSyncPayload::handle);

        // C2S：客户端点击过滤槽位后提交鬼影内容。
        NetworkUtils.registerC2SPayload(
                UpdatePredationFilterPayload.TYPE,
                UpdatePredationFilterPayload.STREAM_CODEC,
                UpdatePredationFilterPayload::handle);

        // S2C：玩家初次获得拉斐尔时打开命名 GUI。
        NetworkUtils.registerS2CPayload(
                OpenRaphaelNamingPayload.TYPE,
                OpenRaphaelNamingPayload.STREAM_CODEC,
                OpenRaphaelNamingPayload::handle);

        // C2S：玩家在拉斐尔命名 GUI 中确认。
        NetworkUtils.registerC2SPayload(
                RequestRaphaelRenamePayload.TYPE,
                RequestRaphaelRenamePayload.STREAM_CODEC,
                RequestRaphaelRenamePayload::handle);

        LOGGER.info("[FoxAblazeUltimate] 加载完成");
    }
}
