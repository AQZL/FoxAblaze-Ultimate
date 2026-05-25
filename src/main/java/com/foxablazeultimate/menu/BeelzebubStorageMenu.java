package com.foxablazeultimate.menu;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.foxablazeultimate.item.beelzebub.CapturedEntityItem;
import com.foxablazeultimate.menu.slot.SharedBucketSlot;
import com.foxablazeultimate.network.RequestBeelzebubFluidExtractPayload;
import com.foxablazeultimate.registry.FoxAblazeUltimateItems;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.subclass.ISpatialStorage;
import io.github.manasmods.tensura.menu.container.SpatialStorageContainer;
import io.github.manasmods.tensura.menu.container.TensuraCraftingContainer;
import io.github.manasmods.tensura.menu.slot.SpatialSlot;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.IAbility;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

public class BeelzebubStorageMenu extends RecipeBookMenu<CraftingInput, CraftingRecipe> {

    public static final int SPATIAL_SLOT_START = 50;

    public static final int BUCKET_INPUT_SLOT  = 48;
    public static final int BUCKET_OUTPUT_SLOT = 49;

    private static final int BUCKET_INPUT_X  = 207;
    private static final int BUCKET_INPUT_Y  = 123;
    private static final int BUCKET_OUTPUT_X = 207;
    private static final int BUCKET_OUTPUT_Y = 174;

    private final ManasSkill skill;
    private final Player player;
    private final LivingEntity storageOwner;
    private final int page;
    private final SpatialStorageContainer container;
    private final TensuraCraftingContainer craftSlots;
    private final ResultContainer craftResultSlots;
    private boolean placingRecipe;
    private final SpatialStorageContainer furnaceInputSlots;
    private final Slot furnaceInputSlot;
    private final Slot furnaceResultSlot;
    private final ResultContainer furnaceResultSlots;

    private final SpatialStorageContainer bucketInputSlots;
    public final ResultContainer bucketOutput = new ResultContainer() {
        @Override
        public int getMaxStackSize() {
            return 64;
        }
    };

    public BeelzebubStorageMenu(int id, Inventory inv, LivingEntity storageOwner,
                                SpatialStorageContainer container, ManasSkill skill, int page) {
        super((MenuType<?>) null, id);
        this.skill = skill;
        this.player = inv.player;
        this.storageOwner = storageOwner;
        this.page = page;
        this.container = container;
        container.startOpen(this.player);

        this.craftSlots = new TensuraCraftingContainer(this, 3, 3);
        this.craftResultSlots = new ResultContainer();

        this.furnaceInputSlots = new SpatialStorageContainer(1, 128);
        this.furnaceInputSlot = new Slot(this.furnaceInputSlots, 0, 183, 89) {
            @Override
            public void set(ItemStack stack) {
                super.set(stack);
                BeelzebubStorageMenu.this.slotChangedFurnaceInput();
            }
        };
        this.furnaceResultSlots = new ResultContainer() {
            @Override
            public int getMaxStackSize() {
                return BeelzebubStorageMenu.this.getMaxStack();
            }
        };
        this.furnaceResultSlot = new Slot(this.furnaceResultSlots, 0, 224, 89) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player p, ItemStack s) {
                super.onTake(p, s);
                if (!BeelzebubStorageMenu.this.furnaceInputSlots.isEmpty()) {
                    BeelzebubStorageMenu.this.slotChangedFurnaceInput();
                }
            }
        };

        this.bucketInputSlots = new SpatialStorageContainer(1, 1);

        this.addPlayerInventory(inv);
        this.addCraftingSlots();
        this.addFurnaceSlots();
        this.addBucketSlots();
        this.addSpatialSlots();
    }


    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; ++row) {
            for (int slot = 0; slot < 9; ++slot) {
                this.addSlot(new Slot(inv, slot + row * 9 + 9, 8 + slot * 18, 119 + row * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, 177));
        }
    }

    private void addCraftingSlots() {
        for (int x = 0; x < 3; ++x) {
            for (int y = 0; y < 3; ++y) {
                this.addSlot(new Slot(this.craftSlots, y + x * 3, 175 + y * 18, 32 + x * 18));
            }
        }
        this.addSlot(new ResultSlot(this.player, this.craftSlots, this.craftResultSlots, 0, 232, 50));
    }

    private void addFurnaceSlots() {
        this.addSlot(this.furnaceInputSlot);
        this.addSlot(this.furnaceResultSlot);
    }

    private void addBucketSlots() {
        this.addSlot(new SharedBucketSlot(this, this.bucketInputSlots, 0, BUCKET_INPUT_X, BUCKET_INPUT_Y));
        this.addSlot(new Slot(this.bucketOutput, 0, BUCKET_OUTPUT_X, BUCKET_OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
    }

    private void addSpatialSlots() {
        int slotIndex = 27 * this.page;
        int remaining = this.container.getContainerSize() - this.page * 27;

        for (int i = 0; i < 3 && remaining > 0; ++i) {
            for (int j = 0; j < 9 && remaining > 0; ++j) {
                this.addSlot(new SpatialSlot(this.container, slotIndex, 8 + j * 18, 44 + i * 18));
                ++slotIndex;
                --remaining;
            }
        }
    }

    public int getSpatialSize() {
        return Math.min(this.container.getContainerSize() - this.page * 27, 27);
    }

    public int getMaxStack() {
        return this.container.getMaxStackSize();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    public int getPage() { return this.page; }
    public ManasSkill getSkill() { return this.skill; }
    public Player getPlayer() { return this.player; }
    public LivingEntity getStorageOwner() { return this.storageOwner; }
    public SpatialStorageContainer getContainer() { return this.container; }


    protected static void slotChangedCraftingGrid(AbstractContainerMenu menu, Level level, Player player,
                                                  CraftingContainer craftingContainer, ResultContainer resultContainer,
                                                  @Nullable RecipeHolder<CraftingRecipe> recipeHolder) {
        if (level.isClientSide()) return;
        CraftingInput craftingInput = craftingContainer.asCraftInput();
        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> optional = level.getServer()
                .getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftingInput, level, recipeHolder);
        if (optional.isPresent()) {
            RecipeHolder<CraftingRecipe> recipe = optional.get();
            CraftingRecipe craftingRecipe = recipe.value();
            if (resultContainer.setRecipeUsed(level, serverPlayer, recipe)) {
                ItemStack assembled = craftingRecipe.assemble(craftingInput, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) result = assembled;
            }
        }
        resultContainer.setItem(0, result);
        menu.setRemoteSlot(0, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                menu.containerId, menu.incrementStateId(), 0, result));
    }

    @Override
    public void slotsChanged(Container container) {
        if (!this.placingRecipe) {
            slotChangedCraftingGrid(this, this.player.level(), this.player, this.craftSlots, this.craftResultSlots, null);
        }
    }


    private void slotChangedFurnaceInput() {
        if (!(this.player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel level = serverPlayer.serverLevel();
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) return;

        SingleRecipeInput input = new SingleRecipeInput(this.furnaceInputSlots.getItem(0));
        Optional<RecipeHolder<SmeltingRecipe>> opt = server.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);
        if (opt.isEmpty()) return;

        RecipeHolder<SmeltingRecipe> recipe = opt.get();
        SmeltingRecipe smelting = recipe.value();
        if (!this.furnaceResultSlots.setRecipeUsed(level, serverPlayer, recipe)) return;

        ItemStack result = smelting.assemble(input, level.registryAccess());
        if (!result.isItemEnabled(level.enabledFeatures())) return;

        ItemStack oldResult = this.furnaceResultSlots.getItem(0);
        if (!oldResult.isEmpty() && !oldResult.is(result.getItem())) return;
        if (!this.furnaceResultSlots.setRecipeUsed(level, serverPlayer, opt.get())) return;

        int countToSet = 0;
        int countToRemove = 0;
        int recipeItemCount = result.getCount();
        int resultContainerItemCount = oldResult.getCount();
        int inputSize = this.furnaceInputSlots.getItem(0).getCount();

        if (resultContainerItemCount == this.getMaxStack()) return;
        while (resultContainerItemCount + recipeItemCount + countToSet <= this.getMaxStack()
                && inputSize - countToRemove > 0) {
            countToSet += recipeItemCount;
            ++countToRemove;
        }
        result.setCount(countToSet + resultContainerItemCount);
        this.furnaceInputSlots.getItem(0).shrink(countToRemove);
        this.furnaceResultSlots.setItem(0, result);
        this.setRemoteSlot(0, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                this.containerId, this.incrementStateId(), 0, result));
        this.furnaceResultSlots.awardUsedRecipes(serverPlayer, List.of(result));
        serverPlayer.playNotifySound(SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
    }


    @Override
    public void beginPlacingRecipe() { this.placingRecipe = true; }

    @Override
    public void finishPlacingRecipe(RecipeHolder<CraftingRecipe> recipeHolder) {
        this.placingRecipe = false;
        slotChangedCraftingGrid(this, this.player.level(), this.player, this.craftSlots, this.craftResultSlots, recipeHolder);
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedContents stackedContents) {
        this.craftSlots.fillStackedContents(stackedContents);
    }

    @Override
    public void clearCraftingContent() {
        this.craftSlots.clearContent();
        this.craftResultSlots.clearContent();
    }

    @Override
    public boolean recipeMatches(RecipeHolder<CraftingRecipe> recipeHolder) {
        return recipeHolder.value().matches(this.craftSlots.asCraftInput(), this.player.level());
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.craftResultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public int getResultSlotIndex() { return 0; }

    @Override
    public int getGridWidth() { return this.craftSlots.getWidth(); }

    @Override
    public int getGridHeight() { return this.craftSlots.getHeight(); }

    @Override
    public int getSize() { return 10; }

    @Override
    public RecipeBookType getRecipeBookType() { return RecipeBookType.CRAFTING; }

    @Override
    public boolean shouldMoveToInventory(int i) { return i != this.getResultSlotIndex(); }


    @Override
    public boolean clickMenuButton(Player player, int i) {
        if (i == 0 && this.page > 0) {
            return openPage(player, this.page - 1);
        }
        if (i == 1 && this.page < (this.container.getContainerSize() - 1) / 27) {
            return openPage(player, this.page + 1);
        }
        return false;
    }

    private boolean openPage(Player player, int newPage) {
        ManasSkillInstance instance = getSkillInstance(this.storageOwner);
        if (instance == null || !(instance.getSkill() instanceof ISpatialStorage spatial)) return false;
        this.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            spatial.openSpatialStoragePage(serverPlayer, this.storageOwner, instance, newPage);
        }
        return true;
    }

    @Nullable
    private ManasSkillInstance getSkillInstance(LivingEntity owner) {
        Optional<ManasSkillInstance> opt = SkillAPI.getSkillsFrom(owner).getSkill(this.skill);
        return opt.orElse(null);
    }

    @Override
    public void removed(Player p) {
        ManasSkillInstance instance = getSkillInstance(this.storageOwner);
        if (instance != null && instance.getSkill() instanceof ISpatialStorage spatial) {
            spatial.saveContainer(instance, this.storageOwner, this.container);
        }
        this.container.stopOpen(p);
        this.clearContainer(p, this.craftSlots);
        this.clearContainer(p, this.furnaceInputSlots);
        this.clearContainer(p, this.furnaceResultSlots);
        this.clearContainer(p, this.bucketInputSlots);
        this.clearContainer(p, this.bucketOutput);
        super.removed(p);
    }


    public void extractFluid(ServerPlayer player, byte fluidType) {
        ItemStack carried = this.getCarried();
        if (carried.isEmpty()) return;

        ExtractEntry entry = lookupExtract(carried.getItem(), fluidType);
        if (entry == null) return;

        IAbility ability = TensuraStorages.getAbilityFrom(this.storageOwner);
        double currentPoint = fluidType == RequestBeelzebubFluidExtractPayload.FLUID_WATER
                ? ability.getWaterPoint() : ability.getLavaPoint();
        if (currentPoint < entry.cost()) return;  

        double newPoint = currentPoint - entry.cost();
        if (fluidType == RequestBeelzebubFluidExtractPayload.FLUID_WATER) ability.setWaterPoint(newPoint);
        else ability.setLavaPoint(newPoint);
        ability.markDirty();

        ItemStack filled = entry.output().getDefaultInstance();
        if (carried.getCount() <= 1) {
            this.setCarried(filled);
        } else {
            carried.shrink(1);
            this.setCarried(carried);
            if (!player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
        }

        SoundEvent sound = fluidType == RequestBeelzebubFluidExtractPayload.FLUID_WATER
                ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_FILL_LAVA;
        player.playSound(sound, 1.0F, 1.0F);

        this.broadcastChanges();
    }

    private record ExtractEntry(Item input, double cost, Item output, byte kind) {}

    private static final ExtractEntry[] EXTRACT_TABLE = {
            new ExtractEntry(Items.BUCKET,       3.0, Items.WATER_BUCKET,
                    RequestBeelzebubFluidExtractPayload.FLUID_WATER),
            new ExtractEntry(Items.SPONGE,      18.0, Items.WET_SPONGE,
                    RequestBeelzebubFluidExtractPayload.FLUID_WATER),
            new ExtractEntry(Items.GLASS_BOTTLE, 1.0, Items.POTION,
                    RequestBeelzebubFluidExtractPayload.FLUID_WATER),

            new ExtractEntry(Items.BUCKET,       3.0, Items.LAVA_BUCKET,
                    RequestBeelzebubFluidExtractPayload.FLUID_LAVA),
    };

    private static ExtractEntry lookupExtract(Item input, byte fluidType) {
        for (ExtractEntry e : EXTRACT_TABLE) {
            if (e.input().equals(input) && e.kind() == fluidType) return e;
        }
        return null;
    }


    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (clickType == ClickType.PICKUP
                && dragType == 0
                && slotId >= SPATIAL_SLOT_START
                && slotId < SPATIAL_SLOT_START + getSpatialSize()
                && player instanceof ServerPlayer serverPlayer) {
            Slot slot = this.slots.get(slotId);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stack.getItem() == FoxAblazeUltimateItems.CAPTURED_ENTITY.get()) {
                CapturedEntityItem.ReleaseStatus status =
                        CapturedEntityItem.releaseFromStack(serverPlayer, stack);
                if (status.shouldConsume()) {
                    slot.set(ItemStack.EMPTY);
                    this.broadcastChanges();
                }
                return;
            }
        }
        super.clicked(slotId, dragType, clickType, player);
    }


    @Override
    public ItemStack quickMoveStack(Player p, int i) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(i);
        if (!slot.hasItem()) return copy;

        ItemStack stack = slot.getItem();

        if (i >= SPATIAL_SLOT_START && i < SPATIAL_SLOT_START + getSpatialSize()
                && stack.getItem() == FoxAblazeUltimateItems.CAPTURED_ENTITY.get()
                && p instanceof ServerPlayer serverPlayer) {
            CapturedEntityItem.ReleaseStatus status =
                    CapturedEntityItem.releaseFromStack(serverPlayer, stack);
            if (status.shouldConsume()) {
                slot.set(ItemStack.EMPTY);
                this.broadcastChanges();
            }
            return ItemStack.EMPTY;
        }

        copy = stack.copy();
        int spatialEnd = SPATIAL_SLOT_START + getSpatialSize();

        if (i == 45 || i == 47 || i == BUCKET_OUTPUT_SLOT) {
            stack.getItem().onCraftedBy(stack, this.player.level(), this.player);
            if (!this.moveItemStackTo(stack, 0, 36, false)
                    && !this.moveItemStackTo(stack, SPATIAL_SLOT_START, spatialEnd, false)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (i >= 0 && i < 36) {
            if (i < 27) {
                if (!this.moveItemStackTo(stack, SPATIAL_SLOT_START, spatialEnd, false)
                        && !this.moveItemStackTo(stack, 27, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, SPATIAL_SLOT_START, spatialEnd, false)
                        && !this.moveItemStackTo(stack, 0, 27, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (i >= SPATIAL_SLOT_START && i <= spatialEnd) {
            if (!this.moveItemStackTo(stack, 0, 36, false)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(stack, 0, 27, false)
                    && !this.moveItemStackTo(stack, SPATIAL_SLOT_START, spatialEnd, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(this.player, stack);
        return copy;
    }

    @Override
    protected boolean moveItemStackTo(ItemStack pStack, int pStartIndex, int pEndIndex, boolean pReverseDirection) {
        boolean flag = false;
        int i = pReverseDirection ? pEndIndex - 1 : pStartIndex;

        if (pStack.isStackable()) {
            while (!pStack.isEmpty()) {
                if (pReverseDirection) {
                    if (i < pStartIndex) break;
                } else if (i >= pEndIndex) break;

                Slot slot = this.slots.get(i);
                ItemStack itemstack = slot.getItem();
                if (!itemstack.isEmpty() && slot.mayPlace(pStack) && ItemStack.isSameItemSameComponents(pStack, itemstack)) {
                    int j = itemstack.getCount() + pStack.getCount();
                    int maxSize = i >= SPATIAL_SLOT_START
                            ? this.container.getMaxStackSize()
                            : Math.min(itemstack.getMaxStackSize(), slot.getMaxStackSize());
                    if (j <= maxSize) {
                        pStack.setCount(0);
                        itemstack.setCount(j);
                        slot.setChanged();
                        flag = true;
                    } else if (itemstack.getCount() < maxSize) {
                        pStack.shrink(maxSize - itemstack.getCount());
                        itemstack.setCount(maxSize);
                        slot.setChanged();
                        flag = true;
                    }
                }
                if (pReverseDirection) --i; else ++i;
            }
        }

        if (!pStack.isEmpty()) {
            i = pReverseDirection ? pEndIndex - 1 : pStartIndex;
            while (true) {
                if (pReverseDirection) {
                    if (i < pStartIndex) break;
                } else if (i >= pEndIndex) break;

                Slot pSlot = this.slots.get(i);
                ItemStack stack = pSlot.getItem();
                if (stack.isEmpty() && pSlot.mayPlace(pStack)) {
                    if (pStack.getCount() > pSlot.getMaxStackSize()) {
                        pSlot.set(pStack.split(pSlot.getMaxStackSize()));
                    } else {
                        pSlot.set(pStack.split(pStack.getCount()));
                    }
                    pSlot.setChanged();
                    flag = true;
                    break;
                }
                if (pReverseDirection) --i; else ++i;
            }
        }
        return flag;
    }
}
