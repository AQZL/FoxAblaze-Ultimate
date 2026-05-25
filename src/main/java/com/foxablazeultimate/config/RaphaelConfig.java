package com.foxablazeultimate.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import io.github.manasmods.manascore.config.api.Comment;
import io.github.manasmods.manascore.config.api.ManasConfig;
import io.github.manasmods.manascore.config.api.ManasSubConfig;

/**
 * 智慧之王·拉斐尔 配置文件。
 * <p>所有可调参数均集中于此，玩家可在 {@code config/fox_ultimate/raphael.toml} 中按需修改。
 * 字段名与 {@link RaphaelSettings} 内部完全对应；新增字段时同时更新读取代码即可。
 */
public class RaphaelConfig extends ManasConfig {

    /**
     * 单例容器。<b>必须</b>放在内嵌类里，而不是 {@code RaphaelConfig} 自己的 static 字段：
     * 因为 {@link ManasConfig#save()} 通过 {@code getClass().getDeclaredFields()} 反射所有字段写入 TOML，
     * 不过滤 static，会把 {@code RaphaelConfig} 实例本身当作普通值写入 → TOML 写入器报
     * {@code Unsupported value type: class RaphaelConfig} 并阻止 mod 加载。
     * 内嵌类的 static 字段不会被外层类的 {@code getDeclaredFields()} 看到，因此安全。
     */
    private static final class Holder {
        static RaphaelConfig instance;
        private Holder() {}
    }

    /** 设置全局单例（mod 构造阶段调用一次）。 */
    public static void setInstance(RaphaelConfig cfg) {
        Holder.instance = cfg;
    }

    /** 全局单例访问入口，{@code RaphaelConfig.get().Raphael.xxx}。 */
    public static RaphaelConfig get() {
        return Holder.instance;
    }

    public RaphaelSettings Raphael = new RaphaelSettings();

    @Override
    public String getFileName() {
        return "raphael";
    }

    /** 把所有 fox_ultimate 系列配置统一放在 {@code config/fox_ultimate/} 子目录下，避免污染主配置目录。 */
    @Override
    public Path getConfigPath() {
        return Paths.get("config", "fox_ultimate", getFileName() + ".toml");
    }

    /**
     * 防御性 load。
     * <p>若上一次 mod 加载中途崩溃，可能在磁盘上留下一个 0 字节的 toml 文件；
     * NightConfig 解析空文件会抛 {@code ParsingException: Not enough data available}
     * 阻止 mod 启动。这里在调用父类 load 前主动删除空文件，使其能重建默认配置。
     */
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

    /** 拉斐尔所有可配置项。 */
    public static class RaphaelSettings extends ManasSubConfig {
        // ── 获取条件 ──────────────────────────────────────────
        @Comment("Require player to be a Hero seed (HeroEgg / TrueHero) OR Demon Lord seed (DemonLordSeed / TrueDemonLord) before fusion. Default true.")
        public boolean fusionRequireSpecialRace = true;

        @Comment("Mastery value to assign on fusion (0-100). Note: 'learning' and 'mastering' are TWO DIFFERENT stages in Tensura. mastery<0 = learning phase (cannot use); mastery>=0 = LEARNED, fully usable at base power; mastery>=100 = MASTERED, additionally unlocks isMastered() bonuses (halved magicule / halved cooldowns / increased copy chance). Default 0 grants only the LEARNED stage so the skill is usable immediately but the player must still grind it for mastery bonuses. Set 100 to also skip the grind.")
        public int fusionGrantMastery = 0;

        @Comment("Ticks to wait between the declaration line and the fusion_message. 20 ticks = 1 second. Default 40 (2s).")
        public int fusionDeclarationDelayTicks = 40;

        @Comment("Ticks to wait between the fusion_message and the actual skill grant / forget of source skills. Default 30 (1.5s).")
        public int fusionGrantDelayTicks = 30;

        // ── 主动模式冷却（秒） ────────────────────────────────
        @Comment("Cooldown in seconds after a SUCCESSFUL activation of mode 1/4/5 (unmastered).")
        public int activationCooldownSuccess = 8;
        @Comment("Cooldown in seconds after a FAILED activation of mode 1/4/5 (unmastered).")
        public int activationCooldownFail = 3;
        @Comment("Cooldown in seconds after a SUCCESSFUL activation of mode 1/4/5 (mastered).")
        public int activationCooldownSuccessMastered = 3;
        @Comment("Cooldown in seconds after a FAILED activation of mode 1/4/5 (mastered).")
        public int activationCooldownFailMastered = 1;
        @Comment("Cooldown in seconds after a SUCCESSFUL activation of Separate mode (mode 5, unmastered).")
        public int activationCooldownSeparateSuccess = 4;
        @Comment("Cooldown in seconds after a SUCCESSFUL activation of Separate mode (mode 5, mastered).")
        public int activationCooldownSeparateSuccessMastered = 2;

        // ── 主动模式魔素消耗 ──────────────────────────────────
        @Comment("Magicule cost for Analysis (mode 1).")
        public double magiculeCostAnalysis = 500.0;
        @Comment("Magicule cost for Analysis when mastered.")
        public double magiculeCostAnalysisMastered = 250.0;
        @Comment("Magicule cost for Synthesise (mode 4).")
        public double magiculeCostSynthesise = 300.0;
        @Comment("Magicule cost for Synthesise when mastered.")
        public double magiculeCostSynthesiseMastered = 150.0;
        @Comment("Magicule cost for Separate (mode 5).")
        public double magiculeCostSeparate = 200.0;
        @Comment("Magicule cost for Separate when mastered.")
        public double magiculeCostSeparateMastered = 100.0;

        // ── 被动属性加成（仅勾选时生效） ──────────────────────
        @Comment("Movement speed bonus (multiplied base) when toggled on.")
        public double movementBonus = 0.20;
        @Comment("Attack speed bonus (additive) when toggled on.")
        public double attackSpeedBonus = 1.00;
        @Comment("Chant speed bonus (additive) when toggled on.")
        public double chantSpeedBonus = 5.00;
        @Comment("Auto melee/projectile dodge chance bonus (%) when toggled on.")
        public double dodgeChanceBonus = 80.0;

        @Comment("Learning point bonus when toggled on (unmastered).")
        public double learningBonus = 12.0;
        @Comment("Learning point bonus when toggled on (mastered).")
        public double learningBonusMastered = 18.0;
        @Comment("Mastery point bonus when toggled on (unmastered).")
        public double masteryBonus = 12.0;
        @Comment("Mastery point bonus when toggled on (mastered).")
        public double masteryBonusMastered = 18.0;

        @Comment("Presence Sense level bonus when toggled on (unmastered). 3+ levels can detect invisibility.")
        public double presenceSenseBonus = 5.0;
        @Comment("Presence Sense level bonus when toggled on (mastered).")
        public double presenceSenseBonusMastered = 8.0;

        @Comment("Aura/Magicule gain bonus (% per kill) when toggled on (unmastered).")
        public double epGainBonus = 5.0;
        @Comment("Aura/Magicule gain bonus (% per kill) when toggled on (mastered).")
        public double epGainBonusMastered = 8.0;

        // ── 解析（mode 1） ────────────────────────────────────
        @Comment("Block range to scan for projectiles when copying magic via Analysis.")
        public double analysisProjectileRange = 30.0;
        @Comment("Block range to scan for entities when copying skills via Analysis.")
        public double analysisEntityRange = 10.0;
        @Comment("Base success chance (%) to copy a skill from target via Analysis (unmastered).")
        public double analysisCopyChance = 50.0;
        @Comment("Success chance (%) to copy a skill from target via Analysis (mastered).")
        public double analysisCopyChanceMastered = 100.0;
        @Comment("Allowed namespaces for skill copy. Default ['tensura']. Add modid to whitelist other mods.")
        public List<String> analysisAllowedNamespaces = new ArrayList<>(List.of("tensura"));
        @Comment("Allowed skill type tags for Analysis copy. Valid: intrinsic, common, extra, unique, resistance, ultimate.")
        public List<String> analysisAllowedTypes = new ArrayList<>(List.of("intrinsic", "common", "extra", "unique"));
        @Comment("Blacklist of skill IDs (e.g. 'tensura:great_sage') that can NEVER be copied.")
        public List<String> analysisBlacklist = new ArrayList<>();
        @Comment("Whitelist of UNIQUE skill IDs that can be copied. EMPTY = all unique skills are allowed. When non-empty, ONLY listed unique skills are copiable; non-unique skills are unaffected. Default omits over-tuned / story-critical uniques on purpose; tweak as needed.")
        public List<String> analysisUniqueWhitelist = new ArrayList<>(List.of(
                "tensura:analyst", "tensura:berserker", "tensura:bewilder", "tensura:chef",
                "tensura:commander", "tensura:cook", "tensura:creator", "tensura:degenerate",
                "tensura:engorger", "tensura:envy", "tensura:falsifier", "tensura:fighter",
                "tensura:fusionist", "tensura:gourmand", "tensura:guardian", "tensura:healer",
                "tensura:infinity_prison", "tensura:lust", "tensura:martial_master", "tensura:mathematician",
                "tensura:merciless", "tensura:murderer", "tensura:musician", "tensura:predator",
                "tensura:seeker", "tensura:shadow_striker", "tensura:sniper", "tensura:spearhead",
                "tensura:suppressor", "tensura:survivor", "tensura:traveler", "tensura:thrower",
                "tensura:tuner", "tensura:usurper", "tensura:villain"
        ));
    }
}
