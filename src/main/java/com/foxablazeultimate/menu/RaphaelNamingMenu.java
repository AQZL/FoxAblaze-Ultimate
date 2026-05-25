package com.foxablazeultimate.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * 智慧之王 · 拉斐尔命名 GUI 的 menu。
 *
 * <h3>定位</h3>
 * <p>沿用 Tensura 原版 {@code NamingMenu} 的"无槽位仅承载实体"思路，但把"目标实体"改成
 * "持有拉斐尔的玩家本人"——本 menu 不做命名生效（命名结果通过 C2S
 * {@link com.foxablazeultimate.network.RequestRaphaelRenamePayload} 直接写入技能 instance NBT），
 * 只为客户端 Screen 提供两件事：
 * <ol>
 *   <li>统一的 {@code containerId}，让确认 / 关闭和 NeoForge 容器同步语义保持一致</li>
 *   <li>让 {@code menu.containerId} 在 C2S 校验链中可对齐当前 GUI</li>
 * </ol>
 *
 * <h3>不进 MenuType 注册</h3>
 * <p>与 {@link BeelzebubStorageMenu} 同思路：{@code MenuType} 留 {@code null}，由我们自己用 S2C
 * {@link com.foxablazeultimate.network.OpenRaphaelNamingPayload} 通知客户端打开，
 * 客户端走 {@link com.foxablazeultimate.client.FoxAblazeUltimateClientAccess#handleOpenRaphaelNaming(...)}
 * 实例化，不走 MenuRegistry。这避免再注册一份 MenuType 引入 IPL（IPlayerExtension）等繁文。
 */
public class RaphaelNamingMenu extends AbstractContainerMenu {

    private final Player player;

    public RaphaelNamingMenu(int containerId, Inventory inventory) {
        super((MenuType<?>) null, containerId);
        this.player = inventory.player;
    }

    public Player getPlayer() {
        return this.player;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
