package com.foxablazeultimate.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import io.github.manasmods.manascore.config.api.Comment;
import io.github.manasmods.manascore.config.api.ManasConfig;
import io.github.manasmods.manascore.config.api.ManasSubConfig;

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

        @Comment("Multiplier applied to all Uriel cooldowns when mastered (mastery >= 100). Default 0.7 (cooldowns shortened).")
        public double masteredCooldownMultiplier = 0.7;
        @Comment("Multiplier applied to damage / duration / barrier points / range when mastered. Default 1.5.")
        public double masteredPowerMultiplier = 1.5;

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

        @Comment("Magicule cost per damage point absorbed by Uriel's guard passive. Lower → more efficient. Default 8.")
        public double guardMagiculeCost = 8.0;
        @Comment("Guard fails when attacker's max EP exceeds owner's max EP × this. Default 1.5.")
        public double guardEPGate = 1.5;

        @Comment("Range to target an entity for Anomaly Cleanse (shift+press). Default 8.")
        public double cleanseRange = 8.0;
        @Comment("Cooldown for Anomaly Cleanse (ticks).")
        public int cleanseCooldown = 60;

        @Comment("Range to target an enemy projectile for Law Takeover. Default 32.")
        public double takeoverRange = 32.0;
        @Comment("Cooldown for Law Takeover (ticks).")
        public int takeoverCooldown = 100;
        @Comment("Cooldown applied to the taken-over magic on its original owner (ticks).")
        public int takeoverBrokenMagicCooldown = 600;
        @Comment("Takeover fails when target's max EP exceeds caster's max EP × this. Default 1.0.")
        public double takeoverEPGate = 1.0;

        @Comment("Self barrier points = caster's maxHP × this. Default 4.5 (multiplier).")
        public double barrierSelfMultiplier = 4.5;
        @Comment("Ally barrier points = ally's maxHP × this. Default 3.0.")
        public double barrierAllyMultiplier = 3.0;
        @Comment("Cooldown after applying / removing a barrier (ticks).")
        public int barrierCooldown = 80;
        @Comment("Magicule cost when applying a barrier.")
        public double barrierMagiculeCost = 300.0;

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

        @Comment("Magicule cost per damage point absorbed by Uriel's fault-field passive. Default 6.")
        public double faultFieldMagiculeCost = 6.0;

        @Comment("Water capacity bonus on learn.")
        public float waterCapacity = 30.0F;
        @Comment("Lava capacity bonus on learn.")
        public float lavaCapacity = 20.0F;

        @Comment("Spatial storage size (slots). Default 90.")
        public int spatialStorageSize = 90;
        @Comment("Max stack size in spatial storage. Default 999.")
        public int spatialStorageMaxStackSize = 999;
    }
}
