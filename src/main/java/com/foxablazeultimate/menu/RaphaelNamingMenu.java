package com.foxablazeultimate.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

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
