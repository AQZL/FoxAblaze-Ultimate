package com.foxablazeultimate.client;

import java.util.Optional;

import com.foxablazeultimate.client.screen.BeelzebubStorageScreen;
import com.foxablazeultimate.client.screen.PredationFilterOverlay;
import com.foxablazeultimate.client.screen.RaphaelNamingScreen;
import com.foxablazeultimate.menu.BeelzebubStorageMenu;
import com.foxablazeultimate.menu.RaphaelNamingMenu;
import com.foxablazeultimate.network.OpenBeelzebubStoragePayload;
import com.foxablazeultimate.network.OpenRaphaelNamingPayload;
import com.foxablazeultimate.network.SyncPredationFilterPayload;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.menu.container.SpatialStorageContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 客户端处理别西卜虚数胃袋打开请求的入口。
 * <p>把 client-side Screen / Menu 实例化逻辑隔离在客户端 only 类里，避免在专用服务器加载时
 * 触发 {@code ClassNotFoundException}（{@link OpenBeelzebubStoragePayload#handle} 中通过 {@code Env.CLIENT}
 * 守卫调用，仅客户端 jar 路径上加载到本类）。
 */
public final class FoxAblazeUltimateClientAccess {

    private FoxAblazeUltimateClientAccess() {}

    /**
     * 接到 S2C 时实例化本地 menu + screen 并切到 GUI。
     * <p>关键：使用与服务端一致的 {@code containerId} / 容量 / 页码，否则 NeoForge 的容器同步会错位。
     */
    public static void handleOpenBeelzebubStorage(OpenBeelzebubStoragePayload msg) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Optional<ManasSkillInstance> opt = SkillAPI.getSkillsFrom(player).getSkill(msg.skill());
        if (opt.isEmpty()) return;

        SpatialStorageContainer container = new SpatialStorageContainer(msg.size(), msg.stackSize());
        Entity raw = player.level().getEntity(msg.entityId());
        LivingEntity owner = raw instanceof LivingEntity le ? le : player;

        BeelzebubStorageMenu menu = new BeelzebubStorageMenu(
                msg.containerId(), player.getInventory(), owner, container,
                opt.get().getSkill(), msg.page());
        player.containerMenu = menu;
        Minecraft.getInstance().setScreen(new BeelzebubStorageScreen(menu, player.getInventory(),
                container.getContainerSize(), msg.page()));
    }

    /**
     * 接到 S2C 时实例化拉斐尔命名 menu + screen 并切到 GUI。
     * <p>menu 使用与服务端一致的 {@code containerId}，方便未来扩展（虽然当前命名 GUI 不依赖 menu 同步槽位）。
     * 默认显示名由 payload 给出（在服务端解析 i18n 已不可行——服务端没本地化字符串），由于客户端要的是
     * 翻译后的字面值，{@code defaultName} 实际上是 lang key 的解析结果——具体见
     * {@link com.foxablazeultimate.ability.skill.raphael.RaphaelSkill#sendNamingPrompt}。
     */
    public static void handleOpenRaphaelNaming(OpenRaphaelNamingPayload msg) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        RaphaelNamingMenu menu = new RaphaelNamingMenu(msg.containerId(), player.getInventory());
        player.containerMenu = menu;
        Minecraft.getInstance().setScreen(new RaphaelNamingScreen(menu, player.getInventory(), msg.defaultName()));
    }

    /** 接到服务端推送的捕食过滤器全量内容，交给 overlay 缓存。 */
    public static void handleSyncPredationFilter(SyncPredationFilterPayload msg) {
        PredationFilterOverlay.applySync(msg.slotCount(), msg.items());
    }
}
