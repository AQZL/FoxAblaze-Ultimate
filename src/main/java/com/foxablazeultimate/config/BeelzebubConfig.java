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

public class BeelzebubConfig extends ManasConfig {

    private static final class Holder {
        static BeelzebubConfig instance;
        private Holder() {}
    }

    public static void setInstance(BeelzebubConfig cfg) {
        Holder.instance = cfg;
    }

    public static BeelzebubConfig get() {
        return Holder.instance;
    }

    public BeelzebubSettings Beelzebub = new BeelzebubSettings();

    @Override
    public String getFileName() {
        return "beelzebub";
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

    public static class BeelzebubSettings extends ManasSubConfig {
        @Comment("Require player to be a Hero seed (HeroEgg / TrueHero) OR Demon Lord seed (DemonLordSeed / TrueDemonLord) before fusion. Default true.")
        public boolean fusionRequireSpecialRace = true;

        @Comment("Mastery value to assign on fusion (0-100). Default 0 grants only the LEARNED stage; set 100 to also skip the grind.")
        public int fusionGrantMastery = 0;

        @Comment("Ticks to wait between the declaration line and the fusion_message. 20 ticks = 1 second. Default 40 (2s).")
        public int fusionDeclarationDelayTicks = 40;

        @Comment("Ticks to wait between the fusion_message and the actual skill grant / forget of source skills. Default 30 (1.5s).")
        public int fusionGrantDelayTicks = 30;

        @Comment("Magicule cost for Isolation (mode 2). Default 250.")
        public double magiculeCostIsolation = 250.0;
        @Comment("Magicule cost for Corrosion (mode 3). Default 50.")
        public double magiculeCostCorrosion = 50.0;
        @Comment("Magicule cost for Soul Steal (mode 5). Default 200.")
        public double magiculeCostSteal = 200.0;
        @Comment("Magicule cost for Soul Consume (mode 6, per damage event). Default 100.")
        public double magiculeCostConsume = 100.0;

        @Comment("Cooldown multiplier for ALL beelzebub modes (vs raw Gluttony/Merciless cooldown). Lock decision: 0.7.")
        public double cooldownMultiplier = 0.7;
        @Comment("Damage multiplier applied to Predation / Corrosion / Soul Steal / Soul Consume; also widens Soul Steal HP & EP gates. Lock decision: 1.5.")
        public double damageMultiplier = 1.5;
        @Comment("Range multiplier applied to Predation reach, Receive radius, and Soul Steal radius. Lock decision: 1.5.")
        public double rangeMultiplier = 1.5;
        @Comment("Duration multiplier applied to Isolation negative-effect cleanse window and to Soul Consume's SOUL_DRAIN duration. Lock decision: 1.3.")
        public double durationMultiplier = 1.3;
        @Comment("Extra multiplier stacked on top of the above when the skill is mastered (mastery >= 100). Lock decision: 1.2.")
        public double masteredExtraMultiplier = 1.2;

        @Comment("Pages available at mastery=0 (each page = 27 slots). Default 4 → 108 slots.")
        public int spatialStoragePagesMin = 4;
        @Comment("Pages available at mastery=100% (each page = 27 slots). Default 10 → 270 slots.")
        public int spatialStoragePagesMax = 10;
        @Comment("Max stack size in the imaginary space. Default 666 to match Gluttony's vanilla spatial storage (Beelzebub is the upgraded form, must not feel smaller). Researcher's 128 is a deliberate downgrade for that specific skill — does NOT apply here.")
        public int spatialStorageMaxStackSize = 666;

        @Comment("Cooldown in seconds between two successful captures (anti double-click spam). Default 1.")
        public int captureCooldownSeconds = 1;
        @Comment("If false, players cannot be captured even when their max EP is at or below the captor's. Lock decision: false (forbid).")
        public boolean captureAllowPlayers = false;
        @Comment("If false, vanilla bosses (Wither, Ender Dragon, etc.) cannot be captured. Lock decision: false (forbid).")
        public boolean captureAllowBosses = false;
        @Comment("Entity registry IDs that are NEVER capturable, regardless of EP. Default forbids wandering / villager traders to keep economy intact.")
        public List<String> captureBlacklist = new ArrayList<>(List.of(
                "minecraft:villager",
                "minecraft:wandering_trader"
        ));
        @Comment("If true, captured entities have their TickCount frozen — no aging, growth, breeding, or hunger while stored. Lock decision: true.")
        public boolean captureFreezeTicks = true;

        @Comment("Block distance in front of the player at which the released entity spawns. Default 1.5.")
        public double releaseDistance = 1.5;
        @Comment("If the spawn position is obstructed (suffocation / non-empty space), refund the captured-entity item rather than damaging the world. Lock decision: true (refund).")
        public boolean releaseRefundOnObstruction = true;
    }
}
