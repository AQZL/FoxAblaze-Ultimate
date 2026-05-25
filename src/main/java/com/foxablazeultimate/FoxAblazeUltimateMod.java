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

@Mod(FoxAblazeUltimateMod.MOD_ID)
public class FoxAblazeUltimateMod {

    public static final String MOD_ID = "foxablazeultimate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FoxAblazeUltimateMod(IEventBus modBus, ModContainer modContainer) {

        RaphaelConfig raphaelCfg = new RaphaelConfig();
        RaphaelConfig.setInstance(raphaelCfg);
        ConfigRegistry.registerConfig(raphaelCfg);

        BeelzebubConfig beelzebubCfg = new BeelzebubConfig();
        BeelzebubConfig.setInstance(beelzebubCfg);
        ConfigRegistry.registerConfig(beelzebubCfg);

        UrielConfig urielCfg = new UrielConfig();
        UrielConfig.setInstance(urielCfg);
        ConfigRegistry.registerConfig(urielCfg);

        FoxAblazeGameRules.init();

        UltimateSkillProtector.init();

        FoxAblazeUltimateSkills.register(modBus);
        FoxAblazeUltimateSounds.register(modBus);
        FoxAblazeUltimateItems.register(modBus);
        FoxAblazeUltimateDataComponents.register(modBus);

        NetworkUtils.registerS2CPayload(
                OpenBeelzebubStoragePayload.TYPE,
                OpenBeelzebubStoragePayload.STREAM_CODEC,
                OpenBeelzebubStoragePayload::handle);

        NetworkUtils.registerC2SPayload(
                RequestBeelzebubFluidExtractPayload.TYPE,
                RequestBeelzebubFluidExtractPayload.STREAM_CODEC,
                RequestBeelzebubFluidExtractPayload::handle);

        NetworkUtils.registerS2CPayload(
                SyncPredationFilterPayload.TYPE,
                SyncPredationFilterPayload.STREAM_CODEC,
                SyncPredationFilterPayload::handle);

        NetworkUtils.registerC2SPayload(
                RequestPredationFilterSyncPayload.TYPE,
                RequestPredationFilterSyncPayload.STREAM_CODEC,
                RequestPredationFilterSyncPayload::handle);

        NetworkUtils.registerC2SPayload(
                UpdatePredationFilterPayload.TYPE,
                UpdatePredationFilterPayload.STREAM_CODEC,
                UpdatePredationFilterPayload::handle);

        NetworkUtils.registerS2CPayload(
                OpenRaphaelNamingPayload.TYPE,
                OpenRaphaelNamingPayload.STREAM_CODEC,
                OpenRaphaelNamingPayload::handle);

        NetworkUtils.registerC2SPayload(
                RequestRaphaelRenamePayload.TYPE,
                RequestRaphaelRenamePayload.STREAM_CODEC,
                RequestRaphaelRenamePayload::handle);

        LOGGER.info("[FoxAblazeUltimate] 加载完成");
    }
}
