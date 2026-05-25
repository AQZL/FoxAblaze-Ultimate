package com.foxablazeultimate.ability.skill.raphael;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.config.RaphaelConfig;
import com.foxablazeultimate.network.OpenRaphaelNamingPayload;
import com.foxablazeultimate.registry.FoxAblazeUltimateSkills;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.unique.DegenerateSkill;
import io.github.manasmods.tensura.ability.skill.unique.GreatSageSkill;
import io.github.manasmods.tensura.ability.subclass.IRefining;
import io.github.manasmods.tensura.ability.subclass.IRepeatCrafting;
import io.github.manasmods.tensura.ability.subclass.ISynthesisSeparation;
import io.github.manasmods.tensura.data.TensuraItemTags;
import io.github.manasmods.tensura.data.TensuraSkillTags;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import io.github.manasmods.tensura.menu.UncraftingMenu;
import io.github.manasmods.tensura.menu.container.SpatialStorageContainer;
import io.github.manasmods.tensura.network.s2c.OpenDegenerateMenuPayload;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.player.ITensuraPlayer;
import io.github.manasmods.tensura.storage.unique.ITrulyUnique;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * 究极技能 · 智慧之王·拉斐尔（Raphael）。
 * <p>由精通"大贤者"与"变质者"后融合而成，整合两者全部功能：
 * <ul>
 *   <li><b>TrulyUnique</b>：单一服务器全程仅一名玩家可拥有，由 {@link RaphaelFusion} 在融合时锁定占用</li>
 *   <li><b>勾选启用被动</b>（与主模组思考加速 / 魔力感知一致）：勾选后全部生效</li>
 *   <li>被动：思考加速（移速 +20% / 攻速 +1.0 / 闪避 +80%）</li>
 *   <li>被动：咏唱无效化 —— 任意魔法瞬发，无视魔法本身是否精通（由 MixinMagic 实现）</li>
 *   <li>被动：高速精通（学习 / 精通增益 +20，精通后 +30，远超思考加速）</li>
 *   <li>被动：魔力感知（PRESENCE_SENSE +5，精通后 +8）</li>
 *   <li>被动：饕餮共鸣 —— 击杀敌人获取的存在值 +5%，精通后 +8%（AURA / MAGICULE_GAIN）</li>
 *   <li>学得即解锁全部锻造蓝图（一次性，不受勾选状态影响）</li>
 *   <li>6 个主动模式：解析鉴定 / 解析 / 提炼 / 合成 / 统合 / 分离（不受勾选状态影响）</li>
 * </ul>
 */
public class RaphaelSkill extends Skill
        implements IRefining<RaphaelSkill>, IRepeatCrafting<RaphaelSkill>, ISynthesisSeparation {

    private static final ResourceLocation RAPHAEL_MOVEMENT       = id("movement_speed");
    private static final ResourceLocation RAPHAEL_ATTACK         = id("attack_speed");
    private static final ResourceLocation RAPHAEL_CHANT          = id("chant_speed");
    private static final ResourceLocation RAPHAEL_DODGE_M        = id("melee_dodge");
    private static final ResourceLocation RAPHAEL_DODGE_P        = id("projectile_dodge");
    private static final ResourceLocation RAPHAEL_LEARNING       = id("learning");
    private static final ResourceLocation RAPHAEL_MASTERY        = id("mastery");
    private static final ResourceLocation RAPHAEL_PRESENCE_SENSE = id("presence_sense");
    private static final ResourceLocation RAPHAEL_AURA_GAIN      = id("aura_gain");
    private static final ResourceLocation RAPHAEL_MAGICULE_GAIN  = id("magicule_gain");

    /** 配置快掷。全部参数由 {@link RaphaelConfig} 提供，修改后重启生效。 */
    private static RaphaelConfig.RaphaelSettings cfg() {
        return RaphaelConfig.get().Raphael;
    }

    public RaphaelSkill() {
        super(SkillType.ULTIMATE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "textures/skill/raphael.png");
    }

    @Override
    public MutableComponent getColoredName() {
        // 客户端 dist 上：尝试用本地玩家自己拉斐尔 instance 上的"自定义名"作为显示名。
        // 服务端 dist 上：永远走默认（"智慧之王·拉斐尔"）。
        // 这样实现了"每个玩家自己看到自己起的名字，全局聊天 / 命令日志 / 别人看到的是默认名"的语义。
        //
        // ★ 专用服务器守卫（重要）：原本依赖 try/catch (Throwable) 兜底 ClientCustomNameAccessor 中的
        // Minecraft 类缺失。但实测在专用服务器上 Tensura 的切模式 / 装备 / 聊天提示链每次都会调
        // getColoredName() → 触发 NoClassDefFoundError 抛栈，代价极高，会直接拖垮 RequestAbilityModeChangePacket
        // 的处理流程，表现为「专用服务器切模式无反应」。这里在调用前用 FMLEnvironment.dist 预判，
        // 专服直接走默认分支，不进 ClientCustomNameAccessor 类的 init 流程。
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer()) {
            MutableComponent custom = ClientCustomNameAccessor.tryGetLocalPlayerCustomName();
            if (custom != null) return custom.withStyle(ChatFormatting.RED);
        }

        MutableComponent name = super.getName();
        return name == null ? null : name.withStyle(ChatFormatting.RED);
    }

    /** 实例 NBT 中保存"玩家自定义拉斐尔名"的字段名。 */
    public static final String NBT_CUSTOM_NAME = "RaphaelCustomName";
    /** 实例 NBT 中"已弹过命名 GUI"的标记字段。{@code true} 即不再 onLearnSkill 时弹窗。 */
    public static final String NBT_NAMED_FLAG = "RaphaelNamed";

    /**
     * 把 instance NBT 上的自定义名读出（无字段或空白返回 null）。
     * <p>纯读取无副作用，可在客户端任意线程调用；服务端调用同等安全。
     */
    public static String readCustomName(ManasSkillInstance instance) {
        CompoundTag tag = instance.getOrCreateTag();
        if (!tag.contains(NBT_CUSTOM_NAME)) return null;
        String value = tag.getString(NBT_CUSTOM_NAME);
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * 由 {@link com.foxablazeultimate.network.RequestRaphaelRenamePayload} 在服务端线程调用。
     * <ul>
     *   <li>{@code rawName} 为空白 / 与默认名相同 → 视作"清除自定义名"，移除 NBT 字段</li>
     *   <li>否则写入 {@code RaphaelCustomName} 字段</li>
     *   <li>无论何种情况都把 {@code RaphaelNamed=true} 标记写入，避免再次 learnSkill 时再弹窗</li>
     *   <li>{@code instance.markDirty()} 触发同步给客户端，HUD 等 UI 立刻读到新名</li>
     *   <li>给玩家回一条系统消息「已命名为：xxx」/「已恢复默认名」，提供即时反馈</li>
     * </ul>
     */
    public static void applyCustomName(ManasSkillInstance instance, String rawName) {
        applyCustomName(instance, rawName, null);
    }

    /**
     * 三参数重载：{@code player} 非空时给玩家发送一条命名结果系统消息。
     * <p>无玩家上下文时（理论上仅在程序化调用 / 单元测试场景）静默；不影响 NBT 写入。
     */
    public static void applyCustomName(ManasSkillInstance instance, String rawName, ServerPlayer player) {
        CompoundTag tag = instance.getOrCreateTag();
        boolean cleared;
        if (rawName == null || rawName.isBlank()) {
            tag.remove(NBT_CUSTOM_NAME);
            cleared = true;
        } else {
            tag.putString(NBT_CUSTOM_NAME, rawName);
            cleared = false;
        }
        tag.putBoolean(NBT_NAMED_FLAG, true);
        instance.markDirty();

        if (player != null) {
            MutableComponent feedback = cleared
                    ? Component.translatable("foxablazeultimate.skill.raphael.naming.cleared")
                            .withStyle(ChatFormatting.GRAY)
                    : Component.translatable("foxablazeultimate.skill.raphael.naming.applied",
                            Component.literal(rawName).withStyle(ChatFormatting.RED))
                            .withStyle(ChatFormatting.GRAY);
            player.sendSystemMessage(feedback);
        }
    }

    /**
     * 服务端在玩家首次获得拉斐尔时调用，把命名 GUI 弹给客户端。
     * <ul>
     *   <li>已经有 {@code RaphaelNamed=true} 标记 → 不再弹窗，避免遗忘 / 复活 / 重读 NBT 等场景反复触发</li>
     *   <li>非 ServerPlayer（如 mob / fake-player） → 不弹</li>
     *   <li>默认名直接用 {@code skill.getName()} 翻译键的服务端解析；客户端会再用本地翻译呈现</li>
     * </ul>
     */
    public static void sendNamingPrompt(ServerPlayer player, ManasSkillInstance instance) {
        CompoundTag tag = instance.getOrCreateTag();
        if (tag.getBoolean(NBT_NAMED_FLAG)) return;

        ManasSkill skill = instance.getSkill();
        ResourceLocation skillId = skill.getRegistryName();
        if (skillId == null) return;

        // 默认名走 lang key —— 这是 ManasSkill.getName() 的服务端表达；客户端会再用本地翻译呈现
        // 实际显示在 EditBox 里的是该 key 翻译后的字符串；payload 中以 lang key 直接传过去由客户端翻译
        String defaultLangKey = String.format("%s.skill.%s",
                skillId.getNamespace(), skillId.getPath().replace('/', '.'));

        player.nextContainerCounter();
        // 不创建 server-side menu —— 因为没有任何同步槽位的需要；客户端 menu 自管 containerId 即可
        NetworkManager.sendToPlayer(player,
                new OpenRaphaelNamingPayload(player.containerCounter, defaultLangKey, skillId));
    }

    /**
     * 客户端 dist 视角的本地玩家自定义名读取器。
     * <p>放成嵌套 static class 是为了让 {@link #getColoredName()} 在专用服务器上不会触发
     * {@code Minecraft.getInstance()} 的类加载（专用服务器上整个 net.minecraft.client 包都缺）。
     * <p>JVM 只有真正引用到 {@link ClientCustomNameAccessor#tryGetLocalPlayerCustomName} 时才会
     * 加载本类；服务端进 {@code getColoredName()} 时下面 catch (Throwable) 兜底返回 null，UI 走默认名。
     */
    private static final class ClientCustomNameAccessor {
        /**
         * @return 当前本地玩家拉斐尔 instance 上的自定义名（红色样式之外的部分），无则返回 null。
         */
        static MutableComponent tryGetLocalPlayerCustomName() {
            try {
                net.minecraft.client.player.LocalPlayer local =
                        net.minecraft.client.Minecraft.getInstance().player;
                if (local == null) return null;
                ManasSkill raphael = FoxAblazeUltimateSkills.RAPHAEL.get();
                if (raphael == null) return null;
                java.util.Optional<ManasSkillInstance> opt = SkillAPI.getSkillsFrom(local).getSkill(raphael);
                if (opt.isEmpty()) return null;
                String custom = readCustomName(opt.get());
                if (custom == null) return null;
                return Component.literal(custom);
            } catch (Throwable ignored) {
                // 任何客户端类缺失 / 未初始化场景 → 走默认名
                return null;
            }
        }
    }

    /**
     * 硬关闭"魔素累积自然觉醒"路径：返回 +∞ 让 Tensura
     * {@code AbilityHandler.UNLOCK_SKILL} 中的 {@code magicule.getValue() <= cost}
     * 永远成立，普通 unlock 一律 interruptFalse。
     * <p>融合是<b>唯一</b>合法获取路径；该路径在
     * {@link RaphaelFusion#doGrant} 调用 {@code learnSkill} 之前向 instance 写入
     * {@code NoMagiculeCost = true} NBT 标记以单次绕过此门槛。
     */
    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return Double.POSITIVE_INFINITY;
    }

    /** 按模式返回魔素消耗（精通后减半）。模式 0/2/3 不消耗魔素。 */
    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        boolean mastered = instance.isMastered(entity);
        RaphaelConfig.RaphaelSettings c = cfg();
        return switch (mode) {
            case 1 -> mastered ? c.magiculeCostAnalysisMastered : c.magiculeCostAnalysis;
            case 4 -> mastered ? c.magiculeCostSynthesiseMastered : c.magiculeCostSynthesise;
            case 5 -> mastered ? c.magiculeCostSeparateMastered : c.magiculeCostSeparate;
            default -> 0.0;
        };
    }

    /** 不允许通过自然 EP 达标自动学习。 */
    @Override
    public boolean checkAcquiringRequirement(net.minecraft.world.entity.player.Player entity, double newEP) {
        return false;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0;
    }

    /** 主动模式数量。仅有此一处定义，避免 nextMode / getModeId / 边界判断各处各写一份。 */
    private static final int MODE_COUNT = 6;

    /**
     * 把任意 int 归一化到 [0, MODE_COUNT)。
     * <p>用 {@link Math#floorMod(int, int)} 是为了正确处理负数（{@code -1 % 6 == -1} 而 floorMod 给 5）。
     * 触发场景：玩家在更早版本里把 mode 切到了 ≥ MODE_COUNT 的旧值（NBT 残留），或者别的 mod / 命令注入了非法值。
     */
    private static int sanitizeMode(int mode) {
        return Math.floorMod(mode, MODE_COUNT);
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_COUNT;
    }

    /**
     * Tensura 调用此方法仅传入「当前 slot 中存储的 mode」，并不会先做边界检查；
     * 一旦 NBT 里残留越界值（例如旧版本 {@code getModes() = 7} 时切到 6），
     * 原实现会一直 {@code mode + 1}，永远跳不回 0-5，
     * 最终触发 {@link #getModeId} 的 default 分支让聊天框显示 raw key {@code foxablazeultimate.skill.mode.default}。
     * 这里先归一化再算下一个，保证任何输入都能落回合法环。
     */
    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        int safe = sanitizeMode(mode);
        if (reverse) {
            return safe == 0 ? MODE_COUNT - 1 : safe - 1;
        }
        return safe == MODE_COUNT - 1 ? 0 : safe + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (sanitizeMode(mode)) {
            case 0 -> "raphael.analytical_appraisal";
            case 1 -> "raphael.analysis";
            case 2 -> "raphael.refine";
            case 3 -> "raphael.crafting";
            case 4 -> "raphael.synthesise";
            case 5 -> "raphael.separate";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        // 被动勾选时需要 tick 以累计精通；精通 / 提炼 / 重复合成状态下也需 tick。
        if (instance.isToggled()) return true;
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) return false;
        CompoundTag tag = instance.getTag();
        return tag != null && (tag.getBoolean("Brewing") || tag.getBoolean("Repeating"));
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (instance.isToggled()) {
            gainMastery(instance, entity);
        }
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            if (this.onRepeatCraftingTick(instance, player)) {
                gainMastery(instance, entity);
            }
            if (this.onRefiningTick(instance, player)) {
                gainMastery(instance, entity);
            }
        }
    }

    /**
     * 解析（mode 1）的执行结果，决定外层如何收尾。
     * <p>与 Tensura {@code GreatSageSkill} onPressed mode 1 保持一致：
     * <ul>
     *   <li>{@link #SUCCESS} —— 复制成功：上成功冷却 + 加精通点</li>
     *   <li>{@link #FAIL_COOLDOWN} —— 普通失败（命中率未过 / 目标无可复制技能 / 学习失败等）：上失败冷却</li>
     *   <li>{@link #FAIL_NO_COOLDOWN} —— 「无目标 / 目标无敌」这类玩家误操作：不上冷却，只提示</li>
     * </ul>
     */
    private enum AnalysisOutcome {
        SUCCESS,
        FAIL_COOLDOWN,
        FAIL_NO_COOLDOWN
    }

    /**
     * 自定义解析（mode 1）：复制目标实体 / 投射物魔法的技能。
     * <p>与大贤者 mode 1 的差异：
     * <ul>
     *   <li>支持复制 UNIQUE 类型技能（默认开启，可在配置中关闭）</li>
     *   <li>命名空间过滤（默认仅 {@code tensura}，可扩展其他 modid）</li>
     *   <li>类型与 ID 黑名单完全可配置</li>
     * </ul>
     * <p><b>失败反馈全面对齐 Tensura</b>：各失败路径都发对应提示 + 播 GENERIC_CAST_FAIL 音效；
     * 返回 {@link AnalysisOutcome} 告诉外层该上哪种冷却。
     */
    private AnalysisOutcome doAnalysis(ManasSkillInstance instance, LivingEntity entity) {
        RaphaelConfig.RaphaelSettings c = cfg();

        // —— 第一步：尝试复制投射物上的魔法技能（与大贤者一致，保留兼容性）——
        TensuraProjectile projectile = ObjectSelectionHelper.getTargetingEntity(
                TensuraProjectile.class, entity, c.analysisProjectileRange, 0.5, true, true, false);
        if (projectile != null && projectile.isAlive()) {
            ManasSkillInstance projSkill = projectile.getSkill();
            if (projSkill != null && projSkill.getMastery() >= 0.0
                    && projSkill.is(TensuraSkillTags.COPIABLE_MAGIC)) {
                entity.swing(InteractionHand.MAIN_HAND, true);
                return tryCopySkill(instance, entity, projSkill, projectile.getOwner());
            }
            // 投射物存在但其技能不可复制 → 与 Tensura 一致弹 activation_failed
            sendFail(entity, "tensura.ability.activation_failed");
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }

        // —— 第二步：尝试复制实体身上的技能 ——
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, c.analysisEntityRange, false);
        if (target == null || !target.isAlive()) {
            // 完全没瞄到目标 → Tensura 此处仅发提示、不播音效、不上 CD
            sendFail(entity, "tensura.targeting.not_targeted");
            return AnalysisOutcome.FAIL_NO_COOLDOWN;
        }
        if (target instanceof net.minecraft.world.entity.player.Player p
                && p.getAbilities().invulnerable) {
            // 创造模式 / invulnerable 玩家 → not_allowed + CAST_FAIL，不上 CD
            sendFail(entity, "tensura.targeting.not_allowed");
            playCastFail(entity);
            return AnalysisOutcome.FAIL_NO_COOLDOWN;
        }

        entity.swing(InteractionHand.MAIN_HAND, true);

        // —— 命中率检定 ——
        double chance = instance.isMastered(entity) ? c.analysisCopyChanceMastered : c.analysisCopyChance;
        if (entity.getRandom().nextInt(100) > chance) {
            sendFail(entity, "tensura.ability.activation_failed");
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }

        // —— 收集所有可被复制的技能（满足配置过滤）——
        List<ManasSkillInstance> copiable = SkillAPI.getSkillsFrom(target).getLearnedSkills().stream()
                .filter(s -> canCopySkill(s, c))
                .toList();
        if (copiable.isEmpty()) {
            sendFail(entity, "tensura.ability.activation_failed.plunder.empty");
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }

        ManasSkillInstance picked = copiable.get(target.getRandom().nextInt(copiable.size()));
        return tryCopySkill(instance, entity, picked, target);
    }

    /**
     * 触发掠夺事件并尝试让 {@code entity} 学到 {@code targetSkill}。
     * <p>Tensura 原版在 plunder 事件被取消时为「静默」分支（无提示文字），这里保留静默但
     * 补一个 CAST_FAIL 音效让玩家至少能感知「跳过」这件事；learnSkill 失败则同 Tensura
     * 发「activation_failed.plunder —— 无法掠夺%s」。
     */
    private static AnalysisOutcome tryCopySkill(ManasSkillInstance instance,
                                                LivingEntity entity,
                                                ManasSkillInstance targetSkill,
                                                net.minecraft.world.entity.Entity sourceEntity) {
        Changeable<ManasSkill> changeable = Changeable.of(targetSkill.getSkill());
        boolean blocked = TensuraSkillEvents.SKILL_PLUNDER.invoker()
                .plunder(sourceEntity, entity, false, changeable).isFalse();
        if (blocked) {
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }
        if (!SkillHelper.learnSkill(entity, changeable.get(), instance.getRemoveTime())) {
            ManasSkill failedSkill = changeable.get();
            entity.sendSystemMessage(Component.translatable(
                            "tensura.ability.activation_failed.plunder", failedSkill.getChatDisplayName(true))
                    .withStyle(ChatFormatting.RED));
            playCastFail(entity);
            return AnalysisOutcome.FAIL_COOLDOWN;
        }

        // swing 已由 doAnalysis 调用，这里只播放成功音效
        SoundEvent castSound = TensuraSoundEvents.GENERIC_CAST.get();
        if (castSound != null) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    castSound, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return AnalysisOutcome.SUCCESS;
    }

    /** 统一发送红色系统提示。 */
    private static void sendFail(LivingEntity entity, String translationKey) {
        entity.sendSystemMessage(Component.translatable(translationKey).withStyle(ChatFormatting.RED));
    }

    /** 统一播放 GENERIC_CAST_FAIL 音效。 */
    private static void playCastFail(LivingEntity entity) {
        SoundEvent fail = TensuraSoundEvents.GENERIC_CAST_FAIL.get();
        if (fail != null) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    fail, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /**
     * 判定一个技能能否被解析复制。
     * <ul>
     *   <li>非临时、已学得（mastery ≥ 0）</li>
     *   <li>不在 {@code NO_PLUNDERING} 标签</li>
     *   <li>命名空间在配置白名单内（默认 {@code tensura}）</li>
     *   <li>类型在配置白名单内（默认含 {@code unique}，故可复制独特技能）</li>
     *   <li>不在配置黑名单内</li>
     * </ul>
     * <p>类型判定使用 {@link Skill.SkillType} 枚举（{@code intrinsic / common / extra / unique / resistance / ultimate}），
     * 非 Tensura 体系的 ManasSkill 由于无法匹配到对应类型，会自动被排除。
     */
    private static boolean canCopySkill(ManasSkillInstance instance, RaphaelConfig.RaphaelSettings c) {
        if (instance.isTemporarySkill()) return false;
        if (instance.getMastery() < 0.0) return false;
        if (instance.is(TensuraSkillTags.NO_PLUNDERING)) return false;

        ResourceLocation rl = instance.getSkill().getRegistryName();
        if (rl == null) return false;

        if (!c.analysisAllowedNamespaces.contains(rl.getNamespace())) return false;
        if (c.analysisBlacklist.contains(rl.toString())) return false;

        // 类型判定：只能复制 Tensura 体系下的 Skill（其他模组的 ManasSkill 没有 SkillType）
        if (!(instance.getSkill() instanceof Skill skill)) return false;
        String typeName = skill.getType().getNamespace();
        boolean typeAllowed = false;
        for (String allowed : c.analysisAllowedTypes) {
            if (allowed != null && allowed.equalsIgnoreCase(typeName)) {
                typeAllowed = true;
                break;
            }
        }
        if (!typeAllowed) return false;

        // 独特技能白名单：仅当类型为 unique 且白名单非空时生效；其他类型不受影响
        if ("unique".equalsIgnoreCase(typeName)
                && !c.analysisUniqueWhitelist.isEmpty()
                && !c.analysisUniqueWhitelist.contains(rl.toString())) {
            return false;
        }
        return true;
    }

    /**
     * 统一处理主动技能（解析 / 统合 / 分离）的冷却与精通增加。
     * 冷却时长、成功判定后是否加精通均走 {@link RaphaelConfig}。
     */
    private static void applyCooldownAndMastery(ManasSkillInstance instance,
                                                LivingEntity entity,
                                                int mode,
                                                boolean activated) {
        boolean mastered = instance.isMastered(entity);
        RaphaelConfig.RaphaelSettings c = cfg();
        int cd;
        if (activated && mode == 5) {
            cd = mastered ? c.activationCooldownSeparateSuccessMastered : c.activationCooldownSeparateSuccess;
        } else {
            cd = activated
                    ? (mastered ? c.activationCooldownSuccessMastered : c.activationCooldownSuccess)
                    : (mastered ? c.activationCooldownFailMastered    : c.activationCooldownFail);
        }
        instance.setCoolDown(cd, mode);
        if (activated) {
            instance.addMasteryPoint(entity);
        }
    }

    /** 与大贤者 / 思考加速一致的精通点累计节奏。 */
    private static void gainMastery(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }
        tag.putInt("activatedTimes", time + 1);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        GreatSageSkill sage = (GreatSageSkill) UniqueSkills.GREAT_SAGE.get();
        DegenerateSkill degen = (DegenerateSkill) UniqueSkills.DEGENERATE.get();

        switch (mode) {
            // 解析鉴定：纯切换显示模式（实体/方块/两者），无冷却
            case 0 -> RaphaelDelegateHelper.delegateOnPressed(
                    instance, RaphaelDelegateHelper.SAGE_TAG, sage, entity, keyNumber, mode);
            // 解析：自定义逻辑 —— 支持复制 UNIQUE 技能、可配置命名空间 / 类型 / 黑名单，失败反馈全面对齐 Tensura
            case 1 -> {
                if (instance.onCoolDown(mode)) return;
                if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
                AnalysisOutcome outcome = doAnalysis(instance, entity);
                switch (outcome) {
                    case SUCCESS         -> applyCooldownAndMastery(instance, entity, mode, true);
                    case FAIL_COOLDOWN   -> applyCooldownAndMastery(instance, entity, mode, false);
                    case FAIL_NO_COOLDOWN -> { /* 「无目标 / 目标无敌」不上冷却，提示与音效已在 doAnalysis 内发出 */ }
                }
            }
            // 提炼：必须用 raphael 自己的 registry name 发送 S2C，避免客户端查不到大贤者导致 GUI 不开
            case 2 -> openRefineOrRepeatCrafting(instance, entity);
            // 合成：同上，走 raphael 自己的 ISynthesisSeparation 实现
            case 3 -> openUncraftingOrSynthesis(instance, entity);
            // 统合 / 分离：消耗魔素，冷却 / 精通走配置
            // mode 4（统合）支持 Shift+按下 切小模式 —— 详见 onSynthesisePressed / cycleSynthesiseSubMode
            case 4 -> onSynthesisePressed(instance, entity, keyNumber, mode, degen);
            case 5 -> {
                if (instance.onCoolDown(mode)) return;
                if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
                boolean activated = RaphaelDelegateHelper.delegateOnPressed(
                        instance, RaphaelDelegateHelper.DEGENERATE_TAG, degen, entity, keyNumber, mode - 3);
                applyCooldownAndMastery(instance, entity, mode, activated);
            }
            default -> {}
        }
    }

    // ===========================================================
    // |  统合（mode 4）小模式：默认统合 / 进化为 ...                |
    // ===========================================================

    /** 统合小模式 NBT 字段名。 */
    public static final String NBT_SYNTHESISE_SUBMODE = "RaphaelSynthSub";

    /** 小模式 0 = 原版统合（对生物 / 卡里布迪斯核心）。 */
    public static final int SYNTH_SUBMODE_DEFAULT = 0;
    /** 小模式 1 = 进化为乌列尔（前置：拉斐尔精通 + 无限牢狱精通）。 */
    public static final int SYNTH_SUBMODE_EVOLVE_URIEL = 1;

    /**
     * 处理 mode 4（统合）的按下。
     * <ul>
     *   <li><b>Shift + 按下</b>：切换小模式。仅在「玩家具备的进化目标列表」非空时存在第二个小模式。
     *       不消耗魔素 / 不上冷却（与捕食者 Shift 切 blockMode 同款体验）。每次切换在 ActionBar 反馈当前小模式名。</li>
     *   <li><b>普通按下</b>：按当前小模式触发：
     *       <ul>
     *         <li>{@link #SYNTH_SUBMODE_DEFAULT} → 走原有 delegate 到 DegenerateSkill mode 1 路径</li>
     *         <li>{@link #SYNTH_SUBMODE_EVOLVE_URIEL} → 进化为乌列尔（前置不满足时静默回落到默认行为，
     *             避免「Shift 切完没反应」让玩家困惑）</li>
     *       </ul>
     *   </li>
     * </ul>
     */
    private void onSynthesisePressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber,
                                     int mode, DegenerateSkill degen) {
        if (entity instanceof Player player && player.isShiftKeyDown()) {
            cycleSynthesiseSubMode(instance, player);
            return;
        }

        int subMode = readSynthesiseSubMode(instance);
        if (subMode == SYNTH_SUBMODE_EVOLVE_URIEL && canEvolveToUriel(instance, entity)) {
            // 进化路径：消耗魔素 + 上冷却，然后授予乌列尔
            if (instance.onCoolDown(mode)) return;
            if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
            boolean activated = doEvolveToUriel(entity, instance);
            applyCooldownAndMastery(instance, entity, mode, activated);
            return;
        }

        // 默认统合（含「玩家把小模式切到进化但前置失效」的回退路径）
        if (instance.onCoolDown(mode)) return;
        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
        boolean activated = RaphaelDelegateHelper.delegateOnPressed(
                instance, RaphaelDelegateHelper.DEGENERATE_TAG, degen, entity, keyNumber, mode - 3);
        applyCooldownAndMastery(instance, entity, mode, activated);
    }

    /** 读 NBT 中的当前统合小模式；缺失或非法值视作 {@link #SYNTH_SUBMODE_DEFAULT}。 */
    private static int readSynthesiseSubMode(ManasSkillInstance instance) {
        CompoundTag tag = instance.getOrCreateTag();
        int v = tag.getInt(NBT_SYNTHESISE_SUBMODE);
        return (v == SYNTH_SUBMODE_EVOLVE_URIEL) ? SYNTH_SUBMODE_EVOLVE_URIEL : SYNTH_SUBMODE_DEFAULT;
    }

    /**
     * Shift+按下 mode 4 时调用：在玩家可用的小模式之间循环。
     * <p>「可用列表」永远包含 {@link #SYNTH_SUBMODE_DEFAULT}；
     * {@link #SYNTH_SUBMODE_EVOLVE_URIEL} 仅在玩家可进化为乌列尔时（拉斐尔精通 + 无限牢狱精通且未持有乌列尔）出现。
     * 切换后用 ActionBar 提示当前小模式。
     */
    private void cycleSynthesiseSubMode(ManasSkillInstance instance, Player player) {
        int current = readSynthesiseSubMode(instance);
        int next = current;
        // 当前唯一可能存在的「非默认」小模式是进化为乌列尔；以后追加进化目标时把 if 改成 list 轮换即可
        boolean canUriel = canEvolveToUriel(instance, player);
        if (current == SYNTH_SUBMODE_DEFAULT && canUriel) {
            next = SYNTH_SUBMODE_EVOLVE_URIEL;
        } else {
            next = SYNTH_SUBMODE_DEFAULT;
        }
        if (next != current) {
            instance.getOrCreateTag().putInt(NBT_SYNTHESISE_SUBMODE, next);
            instance.markDirty();
        }
        announceSynthesiseSubMode(player, next);
    }

    /** 在 ActionBar 上提示当前统合小模式。 */
    private void announceSynthesiseSubMode(Player player, int subMode) {
        Component label = switch (subMode) {
            case SYNTH_SUBMODE_EVOLVE_URIEL -> Component.translatable(
                    "foxablazeultimate.skill.raphael.synthesise.submode.evolve",
                    FoxAblazeUltimateSkills.URIEL.get().getColoredName());
            // 默认小模式直接复用 mode 4 自身的名字（"统合"），避免与 mode key 重复
            default -> Component.translatable("foxablazeultimate.skill.mode.raphael.synthesise");
        };
        player.displayClientMessage(
                Component.translatable("foxablazeultimate.skill.raphael.synthesise.submode.switched", label)
                        .withStyle(ChatFormatting.GOLD),
                true);
    }

    /**
     * 是否满足"进化为乌列尔"前置：
     * <ol>
     *   <li>玩家未持有乌列尔（防止已进化后还能再切到该小模式）</li>
     *   <li>玩家已学得且精通"无限牢狱"</li>
     * </ol>
     * <p>注意：本前置<b>不</b>要求拉斐尔自身精通——这是用户决议（v3.x），目的是降低进化门槛，
     * 让玩家在拉斐尔一拿到手就能立刻进化。
     */
    private static boolean canEvolveToUriel(ManasSkillInstance instance, LivingEntity entity) {
        ManasSkill uriel = FoxAblazeUltimateSkills.URIEL.get();
        if (uriel == null) return false;
        if (SkillUtils.hasSkill(entity, uriel)) return false;
        ManasSkill prison = UniqueSkills.INFINITY_PRISON.get();
        if (prison == null) return false;
        return SkillUtils.isSkillMastered(entity, prison);
    }

    /**
     * 实际执行「拉斐尔统合无限牢狱 → 乌列尔」的进化（占位实现）。
     * <ul>
     *   <li>授予乌列尔（mastery 默认 0，玩家自行打练）</li>
     *   <li>遗忘无限牢狱（被统合掉）</li>
     *   <li><b>保留拉斐尔</b>（用户决议，v3.x）：拉斐尔不消失，玩家进化后同时持有拉斐尔与乌列尔</li>
     * </ul>
     * <p>用 ServerPlayer 路径以保持与 RaphaelFusion.doGrant 一致的写入序：先 markDirty 再 learnSkill。
     * <p>失败（learnSkill 拒绝 / 服务端缺失）返回 false，外层不上冷却也不加精通。
     *
     * @return {@code true} 表示进化成功
     */
    private boolean doEvolveToUriel(LivingEntity entity, ManasSkillInstance instance) {
        ManasSkill uriel = FoxAblazeUltimateSkills.URIEL.get();
        ManasSkill prison = UniqueSkills.INFINITY_PRISON.get();
        if (uriel == null || prison == null) return false;
        if (entity.level().isClientSide()) return false;

        TensuraSkillInstance newInstance = new TensuraSkillInstance(uriel);
        newInstance.setMastery(0);
        // bypass 自然觉醒门槛（同 Raphael / Beelzebub 融合）
        newInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
        newInstance.markDirty();
        if (!SkillHelper.learnSkill(entity, newInstance)) return false;

        // 遗忘前置（被统合掉）；拉斐尔本体保留
        SkillAPI.getSkillsFrom(entity).forgetSkill(prison);

        // 进化完成后，把统合小模式重置回默认，避免玩家继续按下 mode 4 时仍处于"进化"小模式
        // 触发 canEvolveToUriel 已持有判定 → 静默回落到默认行为，但 ActionBar 仍会显示"进化"小模式
        // 名容易让玩家以为还能再进化一次。这里直接归零更干净。
        instance.getOrCreateTag().putInt(NBT_SYNTHESISE_SUBMODE, SYNTH_SUBMODE_DEFAULT);
        instance.markDirty();
        return true;
    }

    /**
     * 模式 2 · 提炼 / 重复合成。Shift = 提炼菜单；默认 = 重复合成菜单。
     * <p>直接调用 {@code this.openXxxMenu(...)}，这样 S2C payload 携带的是拉斐尔本身的 registry name，
     * 客户端能正确查到并开启 GUI（修复 Bug 1）。
     */
    private void openRefineOrRepeatCrafting(ManasSkillInstance instance, LivingEntity entity) {
        if (entity.isShiftKeyDown()) {
            this.openRefiningMenu(entity, instance);
        } else {
            this.openRepeatCraftingMenu(entity, instance);
        }
    }

    /**
     * 模式 3 · 合成 / 统合分离。Shift = 统合分离（附魔）菜单；默认 = 拆解菜单。
     * <p>与 mode 2 同理，走 raphael 自己的接口以保证 GUI 能打开。
     */
    private void openUncraftingOrSynthesis(ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        player.closeContainer();
        if (player.isShiftKeyDown()) {
            this.openSynthesisSeparationMenu(player, instance);
        } else {
            player.nextContainerCounter();
            NetworkManager.sendToPlayer(player, new OpenDegenerateMenuPayload(
                    OpenDegenerateMenuPayload.MenuType.CRAFTING,
                    player.containerCounter, player.getId(), this.getRegistryName()));
            player.containerMenu = new UncraftingMenu(player.containerCounter, player.getInventory(), this);
            player.initMenu(player.containerMenu);
        }
        player.playNotifySound(TensuraSoundEvents.SPATIAL_STORAGE.get(), SoundSource.PLAYERS, 0.75F, 1.0F);
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() < 0.0 || instance.isTemporarySkill()) {
            return;
        }
        // 一次性效果：TrulyUnique 登记 + 全图纸解锁 + 命名 GUI 弹窗。被动属性需手动勾选后才生效。
        if (entity instanceof ServerPlayer player) {
            registerTrulyUnique(player);
            unlockAllSchematics(player);
            // 让玩家给自己拉斐尔起个专属名（仅初次；NBT NAMED_FLAG 标记防重复）
            sendNamingPrompt(player, instance);
        }
    }

    @Override
    public void onSkillMastered(ManasSkillInstance instance, LivingEntity entity) {
        super.onSkillMastered(instance, entity);
        if (instance.isTemporarySkill()) return;
        // 仅在"已勾选"状态下才刷新到精通版。未勾选时不动作，下次勾选会自动取用精通版。
        if (instance.isToggled()) {
            removePassiveAttributes(entity);
            applyPassiveAttributes(entity, true);
        }
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        super.onToggleOn(instance, entity);
        if (instance.isTemporarySkill()) return;
        applyPassiveAttributes(entity, instance.isMastered(entity));
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        super.onToggleOff(instance, entity);
        removePassiveAttributes(entity);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        // 防御性清除：万一在勾选状态下遗忘，避免属性残留。
        removePassiveAttributes(entity);
        if (entity instanceof ServerPlayer player) {
            unregisterTrulyUnique(player);
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer player, boolean conqueredEnd) {
        super.onRespawn(instance, player, conqueredEnd);
        // 只有复活前本来就勾选了才重新贴上被动，保持与其他 toggle 技能一致的体验。
        if (instance.isToggled() && instance.getMastery() >= 0.0 && !instance.isTemporarySkill()) {
            applyPassiveAttributes(player, instance.isMastered(player));
        }
    }

    @Override
    public boolean onDeath(ManasSkillInstance instance, LivingEntity owner, DamageSource source) {
        return true;
    }

    // ===========================================================
    // |          IRefining / IRepeatCrafting / ISpatialStorage    |
    // ===========================================================

    /** 与大贤者一致：20 格、堆叠上限 128。 */
    @Override
    public @NotNull SpatialStorageContainer getSpatialStorage(ManasSkillInstance instance, HolderLookup.Provider provide) {
        SpatialStorageContainer container = new SpatialStorageContainer(20, 128);
        container.fromTag(instance.getOrCreateTag().getList("SpatialStorage", 10), provide);
        return container;
    }

    @Override
    public int getSpatialStorageIdOffset() {
        return 11;
    }

    @Override
    public boolean isAutoRefiningAllowed() {
        return true;
    }

    @Override
    public boolean hasAutoCraftingTab() {
        return true;
    }

    @Override
    public void openSpatialStoragePage(ServerPlayer player, LivingEntity owner, ManasSkillInstance instance, int page) {
        this.openRepeatCraftingMenu(player, owner, instance);
    }

    // ===========================================================
    // |              ISynthesisSeparation 配置代转              |
    // ===========================================================

    @Override
    public int getMaximumBonusLevel() {
        return ((DegenerateSkill) UniqueSkills.DEGENERATE.get()).getMaximumBonusLevel();
    }

    @Override
    public List<String> getSeparateBlacklistEnchantments() {
        return ((DegenerateSkill) UniqueSkills.DEGENERATE.get()).getSeparateBlacklistEnchantments();
    }

    @Override
    public List<String> getSynthesisBlacklistEnchantments() {
        return ((DegenerateSkill) UniqueSkills.DEGENERATE.get()).getSynthesisBlacklistEnchantments();
    }

    @Override
    public List<String> getBonusLevelBlackListEnchantments() {
        return ((DegenerateSkill) UniqueSkills.DEGENERATE.get()).getBonusLevelBlackListEnchantments();
    }

    // ===========================================================
    // |                    TrulyUnique 占用管理                  |
    // ===========================================================

    /**
     * 将本玩家登记为拉斐尔的唯一持有者。
     * <p>由 gamerule {@code raphaelTrulyUnique}（默认 true）控制；关闭时直接返回不写入。
     * <p>由 {@link RaphaelFusion#tryFuse(LivingEntity)} 在融合时已做过冲突检查，
     * 这里只是冗余安全网，确保任何途径（指令 / 调试 / 兼容他模组）获得拉斐尔后
     * 都会写入占用记录。
     */
    private void registerTrulyUnique(ServerPlayer player) {
        if (!RaphaelFusion.isTrulyUniqueEnabled(player)) return;
        ServerLevel overworld = overworldOf(player);
        if (overworld == null) return;
        ITrulyUnique unique = TensuraStorages.getUniqueStorageFrom(overworld);
        ResourceLocation id = this.getRegistryName();
        if (id == null) return;
        UUID currentOwner = unique.getOwner(id);
        if (currentOwner == null || !currentOwner.equals(player.getUUID())) {
            unique.addSkill(id, player.getUUID());
            unique.markDirty();
        }
    }

    /** 仅当 owner 是当前玩家时才解除占用，避免抹去他人的合法持有记录。 */
    private void unregisterTrulyUnique(ServerPlayer player) {
        ServerLevel overworld = overworldOf(player);
        if (overworld == null) return;
        ITrulyUnique unique = TensuraStorages.getUniqueStorageFrom(overworld);
        ResourceLocation id = this.getRegistryName();
        if (id == null) return;
        UUID owner = unique.getOwner(id);
        if (owner != null && owner.equals(player.getUUID())) {
            unique.removeSkill(id);
            unique.markDirty();
        }
    }

    private static ServerLevel overworldOf(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : server.overworld();
    }

    // ===========================================================
    // |                       图纸全解锁                          |
    // ===========================================================

    /** 遍历物品注册表，解锁所有 {@code tensura:schematics} 标签下的锻造蓝图。 */
    private static void unlockAllSchematics(ServerPlayer player) {
        ITensuraPlayer data = TensuraStorages.getPlayerDataFrom(player);
        if (data == null) return;
        boolean anyUnlocked = false;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDefaultInstance().is(TensuraItemTags.SCHEMATICS) && !data.hasSchematic(item)) {
                data.unlockSchematic(item);
                anyUnlocked = true;
            }
        }
        if (anyUnlocked) {
            data.markDirty();
        }
    }

    /**
     * 应用全部被动属性。所有加成都从 {@link RaphaelConfig} 读取。
     * @param mastered 是否处于精通态（决定是否使用 *Mastered 变体）。
     */
    private static void applyPassiveAttributes(LivingEntity entity, boolean mastered) {
        RaphaelConfig.RaphaelSettings c = cfg();
        double learning = mastered ? c.learningBonusMastered      : c.learningBonus;
        double mastery  = mastered ? c.masteryBonusMastered       : c.masteryBonus;
        double sense    = mastered ? c.presenceSenseBonusMastered : c.presenceSenseBonus;
        double epGain   = mastered ? c.epGainBonusMastered        : c.epGainBonus;

        addModifier(entity, Attributes.MOVEMENT_SPEED,                       RAPHAEL_MOVEMENT,       c.movementBonus,     Operation.ADD_MULTIPLIED_BASE);
        addModifier(entity, Attributes.ATTACK_SPEED,                         RAPHAEL_ATTACK,         c.attackSpeedBonus,  Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.CHANT_SPEED,                   RAPHAEL_CHANT,          c.chantSpeedBonus,   Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.AUTO_MELEE_DODGE_CHANCE,       RAPHAEL_DODGE_M,        c.dodgeChanceBonus,  Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE,  RAPHAEL_DODGE_P,        c.dodgeChanceBonus,  Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.ABILITY_LEARNING_GAIN,         RAPHAEL_LEARNING,       learning,            Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.ABILITY_MASTERY_GAIN,          RAPHAEL_MASTERY,        mastery,             Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.PRESENCE_SENSE,                RAPHAEL_PRESENCE_SENSE, sense,               Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.AURA_GAIN,                     RAPHAEL_AURA_GAIN,      epGain,              Operation.ADD_VALUE);
        addModifier(entity, TensuraAttributes.MAGICULE_GAIN,                 RAPHAEL_MAGICULE_GAIN,  epGain,              Operation.ADD_VALUE);
    }

    private static void removePassiveAttributes(LivingEntity entity) {
        removeModifier(entity, Attributes.MOVEMENT_SPEED,                       RAPHAEL_MOVEMENT);
        removeModifier(entity, Attributes.ATTACK_SPEED,                         RAPHAEL_ATTACK);
        removeModifier(entity, TensuraAttributes.CHANT_SPEED,                   RAPHAEL_CHANT);
        removeModifier(entity, TensuraAttributes.AUTO_MELEE_DODGE_CHANCE,       RAPHAEL_DODGE_M);
        removeModifier(entity, TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE,  RAPHAEL_DODGE_P);
        removeModifier(entity, TensuraAttributes.ABILITY_LEARNING_GAIN,         RAPHAEL_LEARNING);
        removeModifier(entity, TensuraAttributes.ABILITY_MASTERY_GAIN,          RAPHAEL_MASTERY);
        removeModifier(entity, TensuraAttributes.PRESENCE_SENSE,                RAPHAEL_PRESENCE_SENSE);
        removeModifier(entity, TensuraAttributes.AURA_GAIN,                     RAPHAEL_AURA_GAIN);
        removeModifier(entity, TensuraAttributes.MAGICULE_GAIN,                 RAPHAEL_MAGICULE_GAIN);
    }

    private static void addModifier(LivingEntity entity,
                                    Holder<Attribute> attr,
                                    ResourceLocation id,
                                    double amount,
                                    Operation op) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) {
            inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
        }
    }

    private static void removeModifier(LivingEntity entity,
                                       Holder<Attribute> attr,
                                       ResourceLocation id) {
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) {
            inst.removeModifier(id);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "raphael/" + path);
    }
}
