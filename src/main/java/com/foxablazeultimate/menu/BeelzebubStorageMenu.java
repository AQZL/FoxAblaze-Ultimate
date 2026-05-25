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

/**
 * 暴食之王 · 虚数空间容器 menu。
 *
 * <h3>功能与 {@code ResearcherStorageMenu} 完全一致</h3>
 * <ul>
 *   <li>3 行 + 9 列 玩家背包 / 9 列 热键栏</li>
 *   <li>3×3 合成 + 1 合成输出格</li>
 *   <li>1 熔炉输入 + 1 熔炉输出格（自动烧炼，与 researcher 一致）</li>
 *   <li>1 共享 water/lava bucket 输入 + 1 共享输出格（v2 新增，为了修复"虚数仓库看不到水/岩浆条"反馈）</li>
 *   <li>3×9 虚数 slot（按 page 翻页）</li>
 * </ul>
 *
 * <h3>与 Researcher 的差异</h3>
 * <ol>
 *   <li>删除 {@code i == -1} 切换附魔界面的逻辑（暴食之王不带附魔台）</li>
 *   <li>新增"点击虚数 slot 上的 {@link CapturedEntityItem} → 直接召唤实体"路径</li>
 *   <li>剔除 {@code IResearcherEnchanter} 依赖（暴食之王不实现该接口）</li>
 * </ol>
 *
 * <h3>slot 索引（仍与 Researcher 前缀对齐以套用 quickMoveStack 算法，只是尾巴插入 bucket 两格）</h3>
 * <ol start="0">
 *   <li>0~26 玩家背包 3 行</li>
 *   <li>27~35 玩家热键栏</li>
 *   <li>36~44 3×3 合成区</li>
 *   <li>45 合成输出格</li>
 *   <li>46 熔炉输入</li>
 *   <li>47 熔炉输出</li>
 *   <li>48 共享 bucket 输入（SharedBucketSlot）</li>
 *   <li>49 共享 bucket 输出（仅 take，不允许手动放）</li>
 *   <li>50+ 虚数 slot（按当前 page 实际数量；最多 27 个）</li>
 * </ol>
 */
public class BeelzebubStorageMenu extends RecipeBookMenu<CraftingInput, CraftingRecipe> {

    /**
     * 虚数 slot 起始索引。v2 插入 bucket 输入 + 输出 两格后该值从 48 后移到 50。
     * <p>另外该常量不允许随意调，几个 quickMoveStack 跳转 / clicked 拦截都依赖它。
     */
    public static final int SPATIAL_SLOT_START = 50;

    /** v2 共享 bucket 输入槽位。 */
    public static final int BUCKET_INPUT_SLOT  = 48;
    /** v2 共享 bucket 输出槽位（只出不进）。 */
    public static final int BUCKET_OUTPUT_SLOT = 49;

    // 坐标（右下 water/lava 站，贴图预留区域实测值）—— 详见 BeelzebubStorageScreen 同名常量
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

    /**
     * v2 共享 bucket 输入容器（只 1 格）。max stack 1 跟 {@link com.foxablazeultimate.menu.slot.SharedBucketSlot} 合谋：
     * 推进-出反应只需一个桶。
     */
    private final SpatialStorageContainer bucketInputSlots;
    /**
     * v2 共享 bucket 输出。作为 public 字段暴露主要是让 SharedBucketSlot 能在 set/decide 时读写（与 Tensura
     * 原版 {@code SpatialStorageMenu.waterStorageOutput} 类似的可见性）。该字段不走合成识别，仅存放水/岩浆产出。
     */
    public final ResultContainer bucketOutput = new ResultContainer() {
        @Override
        public int getMaxStackSize() {
            // bucket / sponge / bottle 等物品本身的限制走 vanilla；这里不干预
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

        // v2 共享 bucket 输入 / 输出
        this.bucketInputSlots = new SpatialStorageContainer(1, 1);

        this.addPlayerInventory(inv);
        this.addCraftingSlots();
        this.addFurnaceSlots();
        this.addBucketSlots();
        this.addSpatialSlots();
    }

    // ===========================================================
    // |                       Slot 布局                          |
    // ===========================================================

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

    /**
     * v2 共享 bucket 两格。Slot 48 = {@link SharedBucketSlot}（接受水/岩浆相关物品，负责处理储量 + 输出），
     * Slot 49 = mayPlace=false 的 result 槽，绑 {@link #bucketOutput}。
     */
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

    // ===========================================================
    // |               合成识别（与 Researcher 一致）              |
    // ===========================================================

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

    // ===========================================================
    // |          熔炉烧炼（与 Researcher 一致：自动持续烧炼）       |
    // ===========================================================

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

    // ===========================================================
    // |              RecipeBookMenu 抽象方法实现                  |
    // ===========================================================

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

    // ===========================================================
    // |                  翻页 + 关闭（清理）                      |
    // ===========================================================

    @Override
    public boolean clickMenuButton(Player player, int i) {
        // 与 Researcher 一致：删除 i == -1（附魔切换）分支，保留 0 上一页 / 1 下一页
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
        // v2: 关闭时也要把 bucket 输入 / 输出入容送还玩家，避免货物丢失
        this.clearContainer(p, this.bucketInputSlots);
        this.clearContainer(p, this.bucketOutput);
        super.removed(p);
    }

    // ===========================================================
    // |             v2 共享 bucket slot：取液体（右键水/熔岩条）        |
    // ===========================================================

    /**
     * 服务端处理"光标拿物品右键水/熔岩条"的抽取请求。
     *
     * <p>由 {@link RequestBeelzebubFluidExtractPayload#handle} 在校验完玩家与 menu 之后调用。
     *
     * <h3>抽取规则（与 Tensura 原版反向项一致）</h3>
     * <ul>
     *   <li><b>水：</b>BUCKET → WATER_BUCKET（每次扣 3），SPONGE → WET_SPONGE（每次扣 18），
     *       GLASS_BOTTLE → POTION/水瓶（每次扣 1）</li>
     *   <li><b>熔岩：</b>BUCKET → LAVA_BUCKET（每次扣 3）</li>
     * </ul>
     *
     * <h3>处理顺序</h3>
     * <ol>
     *   <li>校验：玩家光标 stack 非空、属于该流体的"接收容器"白名单、虚数空间该流体储量足够</li>
     *   <li>扣减：{@code IAbility.setWater/LavaPoint(current - delta)} + markDirty</li>
     *   <li>消耗光标：光标 stack 数量 -1</li>
     *   <li>产出：把"装填后的物品"放到玩家光标。光标在抽取后<b>必为空</b>（ExtractEntry.input.maxStackSize=1，
     *       消耗后空了；或者 stack=2+ 但水桶/熔岩桶/湿海绵本就 maxStackSize=1，所以白名单里只允许 maxStackSize=1
     *       或抽完后会用 carry slot 同步），将装填物挂上去。如果原 stack &gt; 1（玻璃瓶可叠），把多余原物退还到玩家
     *       背包，新装填物挂光标</li>
     *   <li>{@link #broadcastChanges()}：让客户端水/熔岩条重新拉到当前 point 值（条会随 set 后自动重渲）</li>
     * </ol>
     *
     * <p>静默拒绝（光标无变化）的情形：光标空、光标物品不在白名单、储量不够；不发任何系统消息，避免刷屏。
     *
     * @param player    发起请求的玩家（已确认为本 menu 持有者）
     * @param fluidType 0 = 水，1 = 熔岩；payload 已校验
     */
    public void extractFluid(ServerPlayer player, byte fluidType) {
        ItemStack carried = this.getCarried();
        if (carried.isEmpty()) return;

        ExtractEntry entry = lookupExtract(carried.getItem(), fluidType);
        if (entry == null) return;

        IAbility ability = TensuraStorages.getAbilityFrom(this.storageOwner);
        double currentPoint = fluidType == RequestBeelzebubFluidExtractPayload.FLUID_WATER
                ? ability.getWaterPoint() : ability.getLavaPoint();
        if (currentPoint < entry.cost()) return;  // 储量不够，静默拒绝

        // 扣减储量（不会出现负数：上面 currentPoint < cost 已挡住）
        double newPoint = currentPoint - entry.cost();
        if (fluidType == RequestBeelzebubFluidExtractPayload.FLUID_WATER) ability.setWaterPoint(newPoint);
        else ability.setLavaPoint(newPoint);
        ability.markDirty();

        // 处理光标：消耗 1 个原物品；新装填物（同一个，例如装水的桶）按"是否所有原物品已被消耗"挂回光标
        ItemStack filled = entry.output().getDefaultInstance();
        if (carried.getCount() <= 1) {
            // 光标只有 1 个 → 直接替换为装填物
            this.setCarried(filled);
        } else {
            // 光标 > 1（玻璃瓶可堆叠到 64）→ 拆 1 个出来变成装填物；剩余原物保留在光标
            // 装填物本身（POTION/WATER_BUCKET 等）大概率 maxStackSize=1，无法与剩余原物合并，所以塞背包
            carried.shrink(1);
            this.setCarried(carried);
            if (!player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
        }

        // 音效与原版一致：fill 用 BUCKET_FILL/_LAVA（"装入"指 玩家把虚数空间的液体装到自己容器里）
        SoundEvent sound = fluidType == RequestBeelzebubFluidExtractPayload.FLUID_WATER
                ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_FILL_LAVA;
        player.playSound(sound, 1.0F, 1.0F);

        // 推送 carry slot 与水/熔岩条的当前值；状态由 client 渲染时直接读 ability，broadcastChanges 即可
        this.broadcastChanges();
    }

    /** 单条 extract 规则：input 物品（光标拿的容器）→ 扣 cost 点数 → output 物品（装填后的容器）。 */
    private record ExtractEntry(Item input, double cost, Item output, byte kind) {}

    /**
     * 抽取路由表：原 Tensura WaterStorage / LavaStorage 中的反向（负 delta）项。
     * <ul>
     *   <li>水：空桶 (3点 → 水桶), 海绵 (18点 → 湿海绵), 玻璃瓶 (1点 → 水瓶)</li>
     *   <li>岩浆：空桶 (3点 → 熔岩桶)</li>
     * </ul>
     * <p>玻璃瓶产出固定为 vanilla {@code Items.POTION}（uncrafted "Water Bottle"），与原版 fill 表
     * "POTION ↔ GLASS_BOTTLE" 配对一致；不挂任何 PotionContents。
     */
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

    // ===========================================================
    // |          点击直接召唤（覆写 vanilla 左键 / shift）         |
    // ===========================================================

    /**
     * 拦截虚数 slot 上的左键点击：
     * 若 slot 物品是 {@link CapturedEntityItem}，直接召唤到玩家面前并清空 slot；
     * 否则走 vanilla 默认行为（拾起到光标）。
     *
     * <p>仅在 {@link ClickType#PICKUP} + 左键（dragType=0）时拦截，
     * 其他点击（右键 / 拖拽 / 丢弃 / 数字键）保留 vanilla 行为。
     */
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
                    // 涵盖 RELEASED（成功召唤）+ DISCARDED（物品损坏，静默清理）
                    slot.set(ItemStack.EMPTY);
                    this.broadcastChanges();
                }
                // OBSTRUCTED → 保留 stack，玩家换位置再试
                return;
            }
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    // ===========================================================
    // |          quickMoveStack / moveItemStackTo（照搬 Researcher） |
    // ===========================================================

    @Override
    public ItemStack quickMoveStack(Player p, int i) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(i);
        if (!slot.hasItem()) return copy;

        ItemStack stack = slot.getItem();

        // v2: shift+点击 / 数字键转运虚数 slot 上的 CapturedEntityItem → 同样走召唤路径
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

        // i=45 合成输出 / i=47 熔炉输出 / i=49 v2 bucket 输出 —— 都走"产出类"onCraftedBy 路径
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

    /** 重写 moveItemStackTo：虚数区使用 container.maxStackSize（128）作为合并上限。 */
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
