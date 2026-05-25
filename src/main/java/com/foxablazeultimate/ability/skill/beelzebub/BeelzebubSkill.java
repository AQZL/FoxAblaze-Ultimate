package com.foxablazeultimate.ability.skill.beelzebub;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.config.BeelzebubConfig;
import com.foxablazeultimate.menu.BeelzebubStorageMenu;
import com.foxablazeultimate.network.OpenBeelzebubStoragePayload;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.unique.GluttonySkill;
import io.github.manasmods.tensura.ability.subclass.ISpatialStorage;
import io.github.manasmods.tensura.config.ability.skill.UniqueSkillConfig;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.menu.container.SpatialStorageContainer;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.unique.ITrulyUnique;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 究极技能 · 暴食之王·别西卜（Beelzebub）。
 * <p>由精通"暴食者"与"残虐者"后融合而成，整合两者全部功能：
 * <ul>
 *   <li><b>TrulyUnique</b>：单一服务器全程仅一名玩家可拥有，由 {@link BeelzebubFusion} 在融合时锁定占用</li>
 *   <li>7 主动模式（编号 0-6）：捕食 / 虚数空间 / 隔绝 / 腐蚀 / 受领 / 灵魂掠夺 / 灵魂蚕食</li>
 *   <li>虚数空间（mode 1）打开自家 45 格自定义 GUI，与原暴食者 81 格相互独立（融合时旧物品已被 SkillHelper.learnSkill 自动迁移）</li>
 *   <li><b>融合前置仍保留残虐者门槛</b>：玩家必须先精通残虐者；融合后残虐者本体被遗忘，但 SOUL_STEAL / SOUL_CONSUME 功能<b>以增强形态接入</b>（mode 5/6），半径与持续时间按 {@link BeelzebubConfig} 倍率放大</li>
 * </ul>
 */
public class BeelzebubSkill extends Skill implements ISpatialStorage {

    private static final ResourceLocation BEELZEBUB_CORROSION_SPEED = id("corrosion_speed");

    /** 配置快掷。 */
    private static BeelzebubConfig.BeelzebubSettings cfg() {
        return BeelzebubConfig.get().Beelzebub;
    }

    public BeelzebubSkill() {
        super(SkillType.ULTIMATE);
        // mode 3（腐蚀，旧 4）激活时附加移速倍数：与 Tensura GluttonySkill 同款机制，但用我们自己的 ResourceLocation 防 ID 冲突。
        // 倍数读 Tensura 的 corrosionSpeedMultiplier（玩家通过原版 GluttonySkill 配置控制），
        // 阶段 9 已引入 BeelzebubConfig 自己的 *Multiplier 倍率叠乘（getAttributeModifierAmplifier）。
        this.addHeldAttributeModifier(Attributes.MOVEMENT_SPEED, BEELZEBUB_CORROSION_SPEED,
                GluttonySkill.CONFIG.corrosionSpeedMultiplier - 1.0,
                Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "textures/skill/beelzebub.png");
    }

    @Override
    public MutableComponent getColoredName() {
        MutableComponent name = super.getName();
        return name == null ? null : name.withStyle(ChatFormatting.DARK_PURPLE);
    }

    /**
     * 硬关闭"魔素累积自然觉醒"路径：返回 +∞ 让 Tensura
     * {@code AbilityHandler.UNLOCK_SKILL} 的 {@code magicule.getValue() <= cost} 永远成立。
     * <p>融合是<b>唯一</b>合法获取路径；该路径在 {@link BeelzebubFusion#doGrant} 调用 {@code learnSkill}
     * 之前向 instance 写入 {@code NoMagiculeCost = true} NBT 标记以单次绕过此门槛。
     */
    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean checkAcquiringRequirement(net.minecraft.world.entity.player.Player entity, double newEP) {
        return false;
    }

    /**
     * 禁用「勾选被动」开关。
     * <p>Beelzebub 没有任何挂在 {@code isToggled()} 上的被动属性
     * （未重写 {@code onToggleOn / onToggleOff}，也不在 {@code canTick / onTick} 中读 toggled 状态）。
     * <p>Mode 6 「灵魂蚕食」属于<b>槽位绑定型被动</b>：用 {@link #isInSlot} 判定当前激活槽，
     * 与 toggle 状态无关。保留 {@code canBeToggled = true} 只会让 UI 出现一个点了无效果的勾选按钮，
     * 反而误导玩家以为存在「勾选启用」的隐藏被动。
     */
    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return false;
    }

    @Override
    public boolean canScroll(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == 0 && instance.getMastery() >= 0.0;
    }

    /** mode 0 + shift = 切换 blockMode（同 Gluttony），不需要走冷却检查。 */
    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == 0 && entity.isShiftKeyDown();
    }

    private static final int MODE_COUNT = 7;

    private static int sanitizeMode(int mode) {
        return Math.floorMod(mode, MODE_COUNT);
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_COUNT;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        int safe = sanitizeMode(mode);
        if (reverse) {
            return safe == 0 ? MODE_COUNT - 1 : safe - 1;
        }
        return safe == MODE_COUNT - 1 ? 0 : safe + 1;
    }

    /**
     * 7 mode → 翻译 key 映射。lang 文件里以
     * {@code foxablazeultimate.skill.mode.beelzebub.<modeId>} 提供。
     *
     * <p><b>v2 mode 重排</b>：删除原版仅显示「敬请期待」的 mimicry / provide，
     * 接入 Merciless 增强版 steal / consume；isolation/corrosion/receive 编号相应前移。
     */
    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (sanitizeMode(mode)) {
            case 0 -> "beelzebub.predation";
            case 1 -> "beelzebub.stomach";
            case 2 -> "beelzebub.isolation";
            case 3 -> "beelzebub.corrosion";
            case 4 -> "beelzebub.receive";
            case 5 -> "beelzebub.steal";
            case 6 -> "beelzebub.consume";
            default -> super.getModeId(instance, mode);
        };
    }

    /**
     * 各 mode 魔素消耗。除 isolation / corrosion / steal / consume 外其他模式均不消耗。
     * <p>所有数值由 {@link BeelzebubConfig} 提供；mastered 状态下减半（与 Tensura 原版同节奏）。
     */
    @Override
    public double getMagiculeCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        boolean mastered = instance.isMastered(entity);
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double base = switch (sanitizeMode(mode)) {
            case 2 -> c.magiculeCostIsolation;
            case 3 -> c.magiculeCostCorrosion;
            case 5 -> c.magiculeCostSteal;
            case 6 -> c.magiculeCostConsume;
            default -> 0.0;
        };
        return mastered ? base * 0.5 : base;
    }

    /** mode 3 corrosion 激活时移速倍数生效；其他 mode 倍数为 0。 */
    @Override
    public double getAttributeModifierAmplifier(ManasSkillInstance instance, LivingEntity entity,
                                                Holder<Attribute> holder, AttributeTemplate template, int mode) {
        return sanitizeMode(mode) == 3 ? 1.0 : 0.0;
    }

    // ===========================================================
    // |              onHeld / onPressed / onScroll              |
    // ===========================================================

    /**
     * 长按行为。
     * <ul>
     *   <li>0 (predation) → pre-init multiplied range → delegate Gluttony 0 → post 增强 mist (damage)</li>
     *   <li>3 (corrosion) → delegate Gluttony 4 → 在 multiplied radius 内追加一次 multiplied 伤害脉冲</li>
     *   <li>5 (steal) → 自家增强版 steal 实现（不走 delegate，避免与已遗忘的残虐者实例耦合）</li>
     *   <li>其他 mode → 无长按行为</li>
     * </ul>
     * delegate 完毕后把 child cooldown × cooldownMultiplier 同步到 parent，
     * 这样 HUD 上显示的就是已经叠加了"暴食之王"加成后的真实可再次激活时间。
     *
     * <p><b>v2 multiplier 真实生效</b>：原本只 delegate 的话，GluttonySkill.onHeld 内部直接读
     * {@code GluttonySkill.CONFIG.predationDamage / corrosionDamage / corrosionRadius}，BeelzebubConfig
     * 里的 {@code damageMultiplier / rangeMultiplier} 完全没作用 —— 玩家反馈"伤害和范围与 Gluttony 一致"
     * 就是这个原因。这里通过 pre-init NBT、post-delegate setDamage、以及 corrosion 追加脉冲三套手段
     * 把倍率真正应用到位。
     */
    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (instance.onCoolDown(mode)) return false;
        int safe = sanitizeMode(mode);
        GluttonySkill gluttony = (GluttonySkill) UniqueSkills.GLUTTONY.get();
        BeelzebubDelegateHelper.DelegateResult result = switch (safe) {
            case 0 -> {
                preInitPredationRange(instance, entity);
                BeelzebubDelegateHelper.DelegateResult d = BeelzebubDelegateHelper.delegateOnHeld(
                        instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, heldTicks, 0);
                enhancePredationMist(instance, entity);
                yield d;
            }
            case 3 -> {
                BeelzebubDelegateHelper.DelegateResult d = BeelzebubDelegateHelper.delegateOnHeld(
                        instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, heldTicks, 4);
                if (heldTicks % 10 == 0) corrosionExtraPulse(instance, entity, safe);
                yield d;
            }
            case 5 -> {
                boolean active = stealEnhanced(instance, entity, heldTicks, safe);
                yield new BeelzebubDelegateHelper.DelegateResult(active, 0);
            }
            default -> BeelzebubDelegateHelper.DelegateResult.IDLE;
        };
        applyCooldownMultiplier(instance, entity, safe, result.childCooldown());
        return result.activated();
    }

    // ===========================================================
    // |              Predation / Corrosion 倍率增强             |
    // ===========================================================

    /**
     * Predation pre-init：首次激活时 sub-instance.tag.range 还没值（或 &lt; 3.0），
     * Gluttony.onHeld 会把它写成 vanilla 的 {@code CONFIG.predationRange}（默认 10），那玩家第一次用
     * 就感觉范围跟 Gluttony 一模一样。这里抢先把它写成 {@code multipliedDefault}（含 mastered 额外倍率），
     * Gluttony 的 {@code if (range &lt; 3.0)} 分支检测到已有值就跳过，玩家首次激活就是放大范围。
     *
     * <p>之后 onScroll 上限也是 multipliedMax，玩家可继续滚轮把 range 拉到比 vanilla 高的位置。
     *
     * <p><b>NBT 路径修正</b>（v3 hotfix）：sub-instance 的"业务字段"（range / Mist / blockMode 等）
     * 在 {@link ManasSkillInstance#serialize} 中实际落在 {@code "tag"} 子键下，不是 sub-instance 序列化
     * 结构的根。早期版本把 range 写到根，导致 Gluttony 读 sub.tag.range = 0 → 进入 vanilla 重置分支。
     * 现在统一走 {@link #subInstanceBusinessTag(ManasSkillInstance, String)}。
     */
    private void preInitPredationRange(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag businessTag = subInstanceBusinessTag(instance, BeelzebubDelegateHelper.GLUTTONY_TAG);
        if (businessTag.getDouble("range") >= 3.0) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double init = GluttonySkill.CONFIG.predationRange * c.rangeMultiplier;
        if (instance.isMastered(entity)) init *= c.masteredExtraMultiplier;
        businessTag.putDouble("range", init);
        instance.markDirty();
    }

    /**
     * Predation post-delegate 增强：让弹幕的 damage 反映 multiplier。
     * <ul>
     *   <li>{@code TensuraProjectile.setDamage(float)} 设为 {@code predationDamage × damageMul}（幂等：
     *       每 tick 设同样的绝对值，不会复利；首次创建时 spawnPredationMist 用 vanilla 值，本步立即覆写）</li>
     *   <li>attackingRange 由 Gluttony.onHeld 在 spawnPredationMist 时写成 {@code tag.range} —— 我们在
     *       pre-init / onScroll 时已经把 tag.range 推到 multipliedMax，所以这里不需要再动它</li>
     * </ul>
     *
     * <p><b>cast 选 TensuraProjectile 而不是 PredatorMistProjectile</b>：后者 implements
     * {@code software.bernie.geckolib.animatable.GeoEntity}，我们这块不依赖 GeckoLib，直接引用会
     * 让 javac 加载不到 GeoEntity 报 "无法访问" 。setDamage 在父类 TensuraProjectile 上，换个 cast 即可。
     */
    private void enhancePredationMist(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag businessTag = subInstanceBusinessTag(instance, BeelzebubDelegateHelper.GLUTTONY_TAG);
        if (!businessTag.contains("Mist")) return;
        int mistId = businessTag.getInt("Mist");
        if (mistId == 0) return;
        if (!(entity.level().getEntity(mistId) instanceof TensuraProjectile mist)) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double dmgMul = c.damageMultiplier;
        if (instance.isMastered(entity)) dmgMul *= c.masteredExtraMultiplier;
        mist.setDamage((float)(GluttonySkill.CONFIG.predationDamage * dmgMul));
    }

    /**
     * Corrosion 追加伤害脉冲（混合策略）。
     *
     * <p><b>设计背景</b>：Gluttony 的私有 {@code corrosion()} 方法写死读 {@code CONFIG.corrosionDamage}
     * 与 {@code CONFIG.corrosionRadius}，没有任何 NBT/参数 hook 可注入倍率。完全 inline 重写又会把技能掠夺
     * (StarvedSkill plunder) + EP steal + predationList 等几十行业务搬过来，引入大量重复代码与同步成本。
     *
     * <p><b>采用方案</b>：保留 delegate 跑 vanilla 逻辑（含 plunder / EP steal 全套），然后在我们这一侧每 10 tick
     * 额外发一波 hurt：
     * <ul>
     *   <li>半径：{@code corrosionRadius × rangeMultiplier × (mastered? extraMul : 1)}</li>
     *   <li>伤害：{@code corrosionDamage × damageMultiplier × (mastered? extraMul : 1)}</li>
     * </ul>
     *
     * <p><b>为什么这样能正确叠加</b>：vanilla {@link LivingEntity#hurt} 在无敌帧期内对同一目标只允许更高的
     * 伤害"补差"（{@code damage - lastHurt}）。delegate 本 tick 的 hurt 把 {@code lastHurt = base = 10}，
     * 我们这里 hurt 把 {@code amount = base × mul = 15}，vanilla 应用 {@code 15 - 10 = 5} 的差额，目标本 tick
     * 总掉血 = 10 + 5 = 15，正好等于 multiplier × base。外圈（vanilla 没打到的）我们这里第一次 hurt，掉血 = 15。
     * 两侧吻合，<b>无双重计算</b>。
     *
     * <p><b>边界折扣</b>：当目标 HP 落在 (base, base × mul] 区间时，delegate hurt 不会致死，plunder 检查
     * {@code target.isDeadOrDying()} 返回 false → 不触发；我们这一侧 hurt 才致死，但我们不跑 plunder。
     * 这个 ~5HP 的灰区影响极小（vs 全套 inline 的代码量得不偿失），玩家几乎察觉不到，留作可接受的 trade-off。
     */
    private void corrosionExtraPulse(ManasSkillInstance instance, LivingEntity entity, int mode) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double radius = GluttonySkill.CONFIG.corrosionRadius * c.rangeMultiplier;
        if (instance.isMastered(entity)) radius *= c.masteredExtraMultiplier;
        float damage = (float)(GluttonySkill.CONFIG.corrosionDamage * c.damageMultiplier);
        if (instance.isMastered(entity)) damage *= (float) c.masteredExtraMultiplier;

        Level level = entity.level();
        List<LivingEntity> list = level.getEntitiesOfClass(
                LivingEntity.class,
                entity.getBoundingBox().inflate(radius),
                (t) -> !t.is(entity) && t.isAlive() && !t.isAlliedTo(entity));
        if (list.isEmpty()) return;

        DamageSource source = ((TensuraDamageSource) this
                .createSource(instance, entity, TensuraDamageTypes.CORROSION, mode))
                .tensura$setDodgeBypass();
        for (LivingEntity target : list) {
            target.hurt(source, damage);
        }
    }

    /**
     * 取 sub-instance 序列化结构的根。仅在需要操作 sub-instance 自身字段（Mastery / Toggled / CooldownList 等
     * "ManasSkillInstance.serialize 直接写到根" 的固定字段）时使用。
     * <p><b>注意</b>：sub-instance 的"业务字段"（即 GluttonySkill 等通过 {@code instance.getOrCreateTag()} 写入的
     * range / Mist / blockMode / predationList 等）<b>不</b>在这个根 compound 上，而是在它的 {@code "tag"} 子键下。
     * 操作业务字段请用 {@link #subInstanceBusinessTag}。
     */
    private static CompoundTag subInstanceRoot(ManasSkillInstance instance, String subTagKey) {
        CompoundTag parentTag = instance.getOrCreateTag();
        if (!parentTag.contains(subTagKey, 10)) {
            parentTag.put(subTagKey, new CompoundTag());
        }
        return parentTag.getCompound(subTagKey);
    }

    /**
     * 取 sub-instance 的"业务字段 tag"，即 sub-instance 在 {@code instance.getOrCreateTag()} 中读写的那块。
     * <p>路径：{@code parent.tag.<subTagKey>.tag} —— 第一层是 sub-instance 序列化结构的根，第二层 {@code "tag"}
     * 子键由 {@link ManasSkillInstance#serialize} 持久化业务 NBT 用。
     * <p>返回的 CompoundTag 是<b>持久化引用</b>：直接对其 put / putInt / putDouble 立即生效，下次
     * {@link BeelzebubDelegateHelper.readSubInstance} 反序列化时会被 sub.deserialize 加载到 sub.tag 字段，
     * GluttonySkill 就能读到我们写入的 range / Mist 等。
     * <p>不存在时<b>会原地新建空 compound 并挂上去</b>，避免调用方拿到游离的临时对象导致写入丢失。
     */
    private static CompoundTag subInstanceBusinessTag(ManasSkillInstance instance, String subTagKey) {
        CompoundTag subRoot = subInstanceRoot(instance, subTagKey);
        if (!subRoot.contains("tag", 10)) {
            subRoot.put("tag", new CompoundTag());
        }
        return subRoot.getCompound("tag");
    }

    /**
     * 按一下行为。
     * <ul>
     *   <li>0 (predation): shift = 切 blockMode；非 shift = 重置 Mist 计数 → 全权委托 Gluttony 0</li>
     *   <li>1 (stomach): 打开自家 45 格虚数空间</li>
     *   <li>2 (isolation): delegate Gluttony 3</li>
     *   <li>3 (corrosion): delegate Gluttony 4（press 在 Gluttony 中无副作用，仅作占位）</li>
     *   <li>4 (receive): delegate Gluttony 5</li>
     *   <li>5 (steal): noop（长按式技能，press 不触发；按一次切到该模式即可）</li>
     *   <li>6 (consume): noop（被动式：在槽位时自动通过 onDamageEntity 触发）</li>
     * </ul>
     */
    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        int safe = sanitizeMode(mode);
        GluttonySkill gluttony = (GluttonySkill) UniqueSkills.GLUTTONY.get();
        BeelzebubDelegateHelper.DelegateResult result = switch (safe) {
            case 0 -> BeelzebubDelegateHelper.delegateOnPressed(
                    instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, keyNumber, 0);
            case 1 -> {
                this.openSpatialStorage(entity, instance);
                yield BeelzebubDelegateHelper.DelegateResult.IDLE;
            }
            case 2 -> BeelzebubDelegateHelper.delegateOnPressed(
                    instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, keyNumber, 3);
            case 3 -> BeelzebubDelegateHelper.delegateOnPressed(
                    instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, keyNumber, 4);
            case 4 -> BeelzebubDelegateHelper.delegateOnPressed(
                    instance, BeelzebubDelegateHelper.GLUTTONY_TAG, gluttony, entity, keyNumber, 5);
            default -> BeelzebubDelegateHelper.DelegateResult.IDLE;
        };
        applyCooldownMultiplier(instance, entity, safe, result.childCooldown());
    }

    // ===========================================================
    // |       Merciless 增强版：灵魂掠夺 (steal) + 灵魂蚕食 (consume)       |
    // ===========================================================

    /**
     * mode 5 · 灵魂掠夺（增强版 {@code MercilessSkill} mode 0 onHeld）。
     * <ul>
     *   <li><b>半径增强</b>：原版 stealRadius × {@code rangeMultiplier}（默认 1.5）</li>
     *   <li><b>触发阈值放宽</b>：HP / EP 触发阈值 × {@code damageMultiplier}（默认 1.5），即更多敌人会被消化</li>
     *   <li><b>恐惧阈值降低</b>：fearLevel 再 -1（更易触发）</li>
     *   <li><b>伤害倍率</b>：原版 10× maxHP × {@code damageMultiplier} → 默认 15× maxHP（保证击杀）</li>
     *   <li>魔素消耗每 20 tick 检查一次（与残虐者节奏一致）</li>
     * </ul>
     *
     * <p>未走 delegate 是因为残虐者本体已在融合时 forget，sub-instance 委托会徒增 NBT 层；
     * 这里直接使用父 Beelzebub instance 计算 mastery / createSource，与隔绝 / 受领等 delegate 路径解耦。
     */
    private boolean stealEnhanced(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return false;
        }
        if (heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0 && heldTicks > 0) {
            instance.addMasteryPoint(entity);
        }

        BeelzebubConfig.BeelzebubSettings c = cfg();
        UniqueSkillConfig.Merciless mc = ConfigRegistry.getConfig(UniqueSkillConfig.class).Merciless;
        double radius      = (double) mc.stealRadius * c.rangeMultiplier;
        double hpThreshold = mc.stealHP * c.damageMultiplier;
        double ownerEPGate = EnergyHelper.getMaxEP(entity) * mc.stealEP * c.damageMultiplier;
        double fearLevel   = Math.max(0.0, mc.stealFear - 2.0); // 原版 -1，增强再 -1
        float damageMul    = (float) (10.0 * c.damageMultiplier);

        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
        TensuraParticleHelper.addServerParticlesAroundSelf(entity,
                (ParticleOptions) TensuraParticleTypes.SOUL.get(), 1.0);

        List<LivingEntity> list = entity.level().getEntitiesOfClass(
                LivingEntity.class,
                entity.getBoundingBox().inflate(radius),
                (t) -> !t.is(entity) && t.isAlive() && !t.isAlliedTo(entity)
                        && !t.getType().is(TensuraEntityTags.NO_SPIRITUAL_DAMAGE));
        if (list.isEmpty()) return true;

        for (LivingEntity target : list) {
            if (target instanceof Player p && p.getAbilities().invulnerable) continue;

            MobEffectInstance fear = target.getEffect(
                    TensuraMobEffects.getReference(TensuraMobEffects.FEAR));
            boolean shouldConsume =
                    (double) target.getHealth() < (double) target.getMaxHealth() * hpThreshold
                            || EnergyHelper.getMaxEP(target) < ownerEPGate
                            || (fear != null && (double) fear.getAmplifier() >= fearLevel);

            if (shouldConsume) {
                DamageSource source = ((TensuraDamageSource) this
                        .createSource(instance, entity, TensuraDamageTypes.SOUL_CONSUMED, mode))
                        .tensura$setDodgeBypass();
                target.hurt(source, target.getMaxHealth() * damageMul);
                TensuraParticleHelper.addServerParticlesAroundSelf(target,
                        (ParticleOptions) TensuraParticleTypes.SOUL.get(), 1.0);
            }
        }
        return true;
    }

    /**
     * mode 6 · 灵魂蚕食（增强版 {@code MercilessSkill} mode 1 onDamageEntity）。
     * 在 mode 6 已绑定至当前 active slot 且仍有魔素时，对每一次实体造成伤害的事件附加 SOUL_DRAIN。
     * <ul>
     *   <li><b>等级增强</b>：drainLevel + 1（原版 1 → 增强 2）</li>
     *   <li><b>持续时间增强</b>：drainDuration × {@code durationMultiplier}（默认 1.3）</li>
     * </ul>
     * 与 Merciless 原版语义一致：本回调始终返回 {@code true} 让伤害事件继续传播；不对 amount 做任何修改。
     */
    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target,
                                  DamageSource source, Changeable<Float> amount) {
        if (!this.isInSlot(owner, instance, 6)) return true;
        if (EnergyHelper.isOutOfEnergy(owner, instance, 6)) return true;

        BeelzebubConfig.BeelzebubSettings c = cfg();
        UniqueSkillConfig.Merciless mc = ConfigRegistry.getConfig(UniqueSkillConfig.class).Merciless;
        int duration = (int) Math.max(1, mc.drainDuration * c.durationMultiplier);
        int level    = Math.max(0, mc.drainLevel); // MobEffectInstance amplifier 是 (level - 1) + 1 增强 = level

        MobEffectInstance soulDrain = new MobEffectInstance(
                TensuraMobEffects.getReference(TensuraMobEffects.SOUL_DRAIN),
                duration, level, false, false, false);
        TensuraMobEffect.addEffect(target, soulDrain, (net.minecraft.world.entity.Entity) owner, this, 6);
        return true;
    }

    /**
     * 把 child instance 写入的冷却同步到 parent，并应用别西卜配置中的 cooldownMultiplier
     * 与 mastered 额外倍率。
     * <p>实际再次激活由 ManasCore tryActivate 检查 parent cd 完成；child cd 仍由 child instance 自管，
     * 用于 child onHeld 内部短路。两边数值以 multiplier 的方式保持一致。
     */
    private static void applyCooldownMultiplier(ManasSkillInstance parent, LivingEntity entity, int mode, int childCooldown) {
        if (childCooldown <= 0) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double mult = c.cooldownMultiplier;
        if (parent.isMastered(entity)) mult *= 1.0 / Math.max(0.0001, c.masteredExtraMultiplier);
        int adjusted = Math.max(1, (int) Math.round(childCooldown * mult));
        parent.setCoolDown(adjusted, mode);
    }

    /**
     * mode 0 滚轮调 predation range。
     * <p><b>不 delegate</b> —— 因为 Gluttony.onScroll 把 max 写死在 {@code CONFIG.predationRange[Mastered]}，
     * 玩家会被锁在 vanilla 上限。这里直接写 sub-instance 业务 NBT 的 {@code range} 字段
     * （Gluttony.onHeld 在 sub-instance 上下文中读它），上限改成
     * {@code base × rangeMultiplier × (mastered ? extraMul : 1)}。
     *
     * <p><b>NBT 路径修正</b>（v3 hotfix）：见 {@link #subInstanceBusinessTag} 注释。
     *
     * <p><b>ActionBar 提示</b>（v3 hotfix）：玩家反馈「滚轮调范围时不像 Gluttony 那样显示当前范围」。
     * 这里在范围真正改变时（newRange != oldRange），用与 Tensura 同款 lang key
     * {@code tensura.skill.range} 在 ActionBar 上提示 "X.X 格"，与 Gluttony 体验一致。
     */
    @Override
    public void onScroll(ManasSkillInstance instance, LivingEntity entity, double delta, int mode) {
        if (sanitizeMode(mode) != 0) return;
        BeelzebubConfig.BeelzebubSettings c = cfg();
        double baseMax = instance.isMastered(entity)
                ? GluttonySkill.CONFIG.predationRangeMastered
                : GluttonySkill.CONFIG.predationRange;
        double maxRange = baseMax * c.rangeMultiplier;
        if (instance.isMastered(entity)) maxRange *= c.masteredExtraMultiplier;

        CompoundTag businessTag = subInstanceBusinessTag(instance, BeelzebubDelegateHelper.GLUTTONY_TAG);
        double oldRange = businessTag.getDouble("range");
        double newRange = oldRange + delta;
        if (newRange > maxRange) newRange = maxRange;
        else if (newRange < 3.0) newRange = 3.0;

        if (oldRange != newRange) {
            businessTag.putDouble("range", newRange);
            instance.markDirty();

            if (entity instanceof Player player) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("tensura.skill.range", newRange)
                                .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.DARK_AQUA)),
                        true);
            }
        }
    }

    // ===========================================================
    // |       学得 / 遗忘：water/lava capacity + TrulyUnique      |
    // ===========================================================

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() < 0.0 || instance.isTemporarySkill()) return;

        // 沿 Gluttony：水 / 岩浆容量加成（暴食者拥有的"无尽胃袋"具象表现）
        AttributeInstance water = entity.getAttribute(TensuraAttributes.WATER_CAPACITY);
        if (water != null) {
            water.setBaseValue(water.getValue() + GluttonySkill.CONFIG.waterCapacity);
        }
        AttributeInstance lava = entity.getAttribute(TensuraAttributes.LAVA_CAPACITY);
        if (lava != null) {
            lava.setBaseValue(lava.getValue() + GluttonySkill.CONFIG.lavaCapacity);
        }

        // TrulyUnique 占用登记
        if (entity instanceof ServerPlayer player) {
            registerTrulyUnique(player);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        if (instance.getMastery() < 0.0) return;

        AttributeInstance water = entity.getAttribute(TensuraAttributes.WATER_CAPACITY);
        if (water != null) {
            water.setBaseValue(Math.max(0.0, water.getValue() - GluttonySkill.CONFIG.waterCapacity));
        }
        AttributeInstance lava = entity.getAttribute(TensuraAttributes.LAVA_CAPACITY);
        if (lava != null) {
            lava.setBaseValue(Math.max(0.0, lava.getValue() - GluttonySkill.CONFIG.lavaCapacity));
        }

        if (entity instanceof ServerPlayer player) {
            unregisterTrulyUnique(player);
        }
    }

    @Override
    public boolean onDeath(ManasSkillInstance instance, LivingEntity owner, DamageSource source) {
        return true;
    }

    // ===========================================================
    // |                 ISpatialStorage 实现                     |
    // ===========================================================

    /**
     * 别西卜虚数空间：页数随 mastery 线性解锁。
     * <ul>
     *   <li>mastery = 0 → {@code pagesMin}（默认 4 页 = 108 slot）</li>
     *   <li>mastery = maxMastery（100%）→ {@code pagesMax}（默认 10 页 = 270 slot）</li>
     *   <li>每页 27 slot，堆叠上限 128（与 researcher 同）</li>
     * </ul>
     *
     * <h3>扩容安全性</h3>
     * <p>Tensura mastery 单调递增，所以每次 getSpatialStorage 返回的 size 也单调递增；
     * 旧 NBT 中最多 prev*27 条 slot 数据，new container 的 size ≥ 旧 size → fromTag 正确 load 前 N 项，
     * 尾部新增 slot 为空。不会丢数据。
     * <p>反向保护：万一 mastery 逻辑导致回退（未来某个 mod 会回收 mastery），取 NBT 中已存在的 slot 数
     * 与计算 size 的最大值，避免物品消失。
     */
    @Override
    public @NotNull SpatialStorageContainer getSpatialStorage(ManasSkillInstance instance, HolderLookup.Provider provide) {
        BeelzebubConfig.BeelzebubSettings c = cfg();
        int pagesMin = Math.max(1, c.spatialStoragePagesMin);
        int pagesMax = Math.max(pagesMin, c.spatialStoragePagesMax);
        int maxMastery = Math.max(1, this.getMaxMastery());
        double ratio = Math.max(0.0, Math.min(1.0, instance.getMastery() / (double) maxMastery));
        int pages = pagesMin + (int) Math.floor(ratio * (pagesMax - pagesMin));
        int size = pages * 27;

        // 防御性：如果 NBT 中已经存了比当前 size 更大的容器（mastery 倒退场景），保留原尺寸不缩
        net.minecraft.nbt.ListTag tag = instance.getOrCreateTag().getList("SpatialStorage", 10);
        int savedMax = 0;
        for (int i = 0; i < tag.size(); i++) {
            int slot = tag.getCompound(i).getByte("Slot") & 255;
            if (slot + 1 > savedMax) savedMax = slot + 1;
        }
        if (savedMax > size) size = ((savedMax + 26) / 27) * 27;  // 向上对齐到整页

        SpatialStorageContainer container = new SpatialStorageContainer(size, c.spatialStorageMaxStackSize);
        container.fromTag(tag, provide);
        return container;
    }

    /**
     * 覆写 {@link ISpatialStorage#openSpatialStoragePage} —— 走自家 {@link BeelzebubStorageMenu}
     * 与 {@link OpenBeelzebubStoragePayload}，绕开 Tensura 默认的 SpatialStorageMenu（带液体槽 + 默认贴图）。
     * <p>实例化时 menu 容器与 payload 中的容量必须保持一致，否则客户端 / 服务端 slot 数错位会同步失败。
     */
    @Override
    public void openSpatialStoragePage(ServerPlayer player, LivingEntity owner, ManasSkillInstance instance, int page) {
        player.nextContainerCounter();
        ManasSkill skill = instance.getSkill();
        SpatialStorageContainer container = this.getSpatialStorage(instance, owner.registryAccess());
        NetworkManager.sendToPlayer(player, new OpenBeelzebubStoragePayload(
                player.containerCounter,
                container.getContainerSize(),
                container.getMaxStackSize(),
                page,
                owner.getId(),
                skill.getRegistryName()));
        player.containerMenu = new BeelzebubStorageMenu(
                player.containerCounter, player.getInventory(), owner, container, skill, page);
        player.initMenu(player.containerMenu);
        // ★ 不在此处播放打开音效 —— ISpatialStorage.openSpatialStorage 接口默认方法
        // 在调用本方法之后已经自动播放过一次 TensuraSoundEvents.SPATIAL_STORAGE @ 0.75F；
        // 如果这里再播一次会与接口默认那次叠加成 ≈1.5F 音量，玩家会感觉"声音偏大"。
        // Researcher 的 openSpatialStoragePage 同样不播音效（让接口默认那次成为唯一播放点）。
    }

    // ===========================================================
    // |                    TrulyUnique 占用管理                  |
    // ===========================================================

    private void registerTrulyUnique(ServerPlayer player) {
        if (!BeelzebubFusion.isTrulyUniqueEnabled(player)) return;
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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "beelzebub/" + path);
    }
}
