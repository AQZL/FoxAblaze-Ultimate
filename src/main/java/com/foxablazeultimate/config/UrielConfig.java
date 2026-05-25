package com.foxablazeultimate.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import io.github.manasmods.manascore.config.api.Comment;
import io.github.manasmods.manascore.config.api.ManasConfig;
import io.github.manasmods.manascore.config.api.ManasSubConfig;

/**
 * 誓约之王·乌列尔（Uriel）配置文件。
 * <p>统合「无限牢狱 + 法则操作 + 多重结界 + 空间支配」四个原技能的功能；本类持有所有可调数值。
 * <p>玩家可在 {@code config/fox_ultimate/uriel.toml} 中按需修改，重启生效。
 *
 * <h3>设计原则</h3>
 * <p>所有数值（伤害 / 范围 / 冷却 / 魔素消耗 / 时长 / 结界点数等）<b>不复用</b>原技能 Tensura 配置类，
 * 全部独立 —— 即便玩家把 Tensura 的 InfinityPrison.toml 改成 0，乌列尔也不受影响。这是为了避免
 * "改原技能配置无意中改了乌列尔"的惊吓。
 *
 * <h3>精通乘数</h3>
 * <p>类似 Beelzebub 的 {@code masteredExtraMultiplier}：精通 100% 时所有 <b>冷却</b> 再乘
 * {@link UrielSettings#masteredCooldownMultiplier}（默认 0.7），所有<b>伤害 / 持续 / 结界点数</b>
 * 再乘 {@link UrielSettings#masteredPowerMultiplier}（默认 1.5）。
 */
public class UrielConfig extends ManasConfig {

    private static final class Holder {
        static UrielConfig instance;
        private Holder() {}
    }

    public static void setInstance(UrielConfig cfg) {
        Holder.instance = cfg;
    }

    public static UrielConfig get() {
        return Holder.instance;
    }

    public UrielSettings Uriel = new UrielSettings();

    @Override
    public String getFileName() {
        return "uriel";
    }

    @Override
    public Path getConfigPath() {
        return Paths.get("config", "fox_ultimate", getFileName() + ".toml");
    }

    @Override
    public void load() {
        Path p = getConfigPath();
        try {
            if (Files.exists(p) && Files.size(p) == 0L) {
                Files.delete(p);
                FoxAblazeUltimateMod.LOGGER.info(
                        "[FoxAblazeUltimate] 已删除残留的空配置 {}，将重建默认值", p);
            }
        } catch (IOException e) {
            FoxAblazeUltimateMod.LOGGER.warn(
                    "[FoxAblazeUltimate] 配置预检失败 ({}): {}", p, e.getMessage());
        }
        super.load();
    }

    public static class UrielSettings extends ManasSubConfig {

        // ── 精通后整体强化 ──
        @Comment("Multiplier applied to all Uriel cooldowns when mastered (mastery >= 100). Default 0.7 (cooldowns shortened).")
        public double masteredCooldownMultiplier = 0.7;
        @Comment("Multiplier applied to damage / duration / barrier points / range when mastered. Default 1.5.")
        public double masteredPowerMultiplier = 1.5;

        // ── 监禁（mode 0）—— 改自无限牢狱 imprison ──
        @Comment("Magicule cost for Imprison.")
        public double magiculeCostImprison = 800.0;
        @Comment("Imprison range in blocks.")
        public double imprisonRange = 12.0;
        @Comment("Imprison duration in ticks (20 = 1 sec). Default 200 (10s); doubles when mastered.")
        public int imprisonDuration = 200;
        @Comment("Cooldown after a successful Imprison (ticks). 20 ticks = 1 sec.")
        public int imprisonCooldown = 200;
        @Comment("Imprison fails when target's max EP exceeds caster's max EP × this. 1.0 = exact parity. Higher → easier to imprison stronger foes.")
        public double imprisonEPGate = 1.0;

        // ── 守护被动 —— 改自无限牢狱 onTakenDamage ──
        @Comment("Magicule cost per damage point absorbed by Uriel's guard passive. Lower → more efficient. Default 8.")
        public double guardMagiculeCost = 8.0;
        @Comment("Guard fails when attacker's max EP exceeds owner's max EP × this. Default 1.5.")
        public double guardEPGate = 1.5;

        // ── 异常清除（mode 2 default）—— 改自法则操作 cleanse ──
        @Comment("Range to target an entity for Anomaly Cleanse (shift+press). Default 8.")
        public double cleanseRange = 8.0;
        @Comment("Cooldown for Anomaly Cleanse (ticks).")
        public int cleanseCooldown = 60;

        // ── 法则夺取（mode 3）—— 改自法则操作 takeover ──
        @Comment("Range to target an enemy projectile for Law Takeover. Default 32.")
        public double takeoverRange = 32.0;
        @Comment("Cooldown for Law Takeover (ticks).")
        public int takeoverCooldown = 100;
        @Comment("Cooldown applied to the taken-over magic on its original owner (ticks).")
        public int takeoverBrokenMagicCooldown = 600;
        @Comment("Takeover fails when target's max EP exceeds caster's max EP × this. Default 1.0.")
        public double takeoverEPGate = 1.0;

        // ── 多重结界（mode 4）—— 改自多重结界 ──
        @Comment("Self barrier points = caster's maxHP × this. Default 4.5 (multiplier).")
        public double barrierSelfMultiplier = 4.5;
        @Comment("Ally barrier points = ally's maxHP × this. Default 3.0.")
        public double barrierAllyMultiplier = 3.0;
        @Comment("Cooldown after applying / removing a barrier (ticks).")
        public int barrierCooldown = 80;
        @Comment("Magicule cost when applying a barrier.")
        public double barrierMagiculeCost = 300.0;

        // ── 维度光线（mode 5）—— 改自空间支配 ray ──
        @Comment("Damage of dimension ray per tick.")
        public float rayDamage = 8.0F;
        @Comment("Range (blocks) of the dimension ray.")
        public float rayRange = 24.0F;
        @Comment("Maximum hold duration in ticks.")
        public float rayDuration = 60.0F;
        @Comment("Cooldown after the ray ends (seconds).")
        public int rayCooldown = 6;
        @Comment("Magicule cost per tick of the ray.")
        public double rayMagiculeCost = 15.0;

        // ── 维度风暴（mode 6）—— 改自空间支配 storm ──
        @Comment("Number of rays in a Dimension Storm. Default 12 (doubles to 24 when mastered).")
        public int stormRayCount = 12;
        @Comment("Damage per ray in storm.")
        public float stormDamage = 25.0F;
        @Comment("Range (blocks) for storm targeting.")
        public double stormRange = 32.0;
        @Comment("Cooldown after a storm cast (seconds).")
        public int stormCooldown = 10;
        @Comment("Magicule cost per storm cast.")
        public double stormMagiculeCost = 600.0;

        // ── 断层场被动 —— 改自空间支配 fault field passive (always-on while in slot) ──
        @Comment("Magicule cost per damage point absorbed by Uriel's fault-field passive. Default 6.")
        public double faultFieldMagiculeCost = 6.0;

        // ── 学得时永久属性增益（参考无限牢狱的水/岩浆容量增加） ──
        @Comment("Water capacity bonus on learn.")
        public float waterCapacity = 30.0F;
        @Comment("Lava capacity bonus on learn.")
        public float lavaCapacity = 20.0F;

        // ── 虚数空间（mode 1） ──
        @Comment("Spatial storage size (slots). Default 90.")
        public int spatialStorageSize = 90;
        @Comment("Max stack size in spatial storage. Default 999.")
        public int spatialStorageMaxStackSize = 999;
    }
}
