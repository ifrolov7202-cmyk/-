package com.martialarts;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Настройки мода: config/martialarts-common.toml
 */
public final class MartialConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue CHI_REGEN_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue SKILLS_HIT_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue PASSIVE_BONUSES;
    public static final ForgeConfigSpec.BooleanValue SCROLLS_IN_LOOT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("balance");
        DAMAGE_MULTIPLIER = b.comment("Множитель урона ударов рукой и техник / Damage multiplier for unarmed hits and techniques")
                .defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        CHI_REGEN_MULTIPLIER = b.comment("Множитель восстановления Ци / Chi regeneration multiplier")
                .defineInRange("chiRegenMultiplier", 1.0, 0.1, 10.0);
        XP_MULTIPLIER = b.comment("Множитель опыта мастерства / Mastery XP multiplier")
                .defineInRange("xpMultiplier", 1.0, 0.1, 20.0);
        SKILLS_HIT_PLAYERS = b.comment("Могут ли техники по площади задевать других игроков / Can area techniques hit other players")
                .define("skillsHitPlayers", true);
        PASSIVE_BONUSES = b.comment("Бонусы мастерства к скорости атаки и бега с пустой рукой / Mastery speed bonuses with empty hand")
                .define("passiveBonuses", true);
        b.pop();
        b.push("world");
        SCROLLS_IN_LOOT = b.comment("Свитки стилей в сундуках подземелий и деревень / Style scrolls in chest loot")
                .define("scrollsInLoot", true);
        b.pop();
        SPEC = b.build();
    }

    private MartialConfig() {}

    /** Безопасное чтение: до загрузки конфига возвращает значение по умолчанию. */
    public static double get(ForgeConfigSpec.DoubleValue v, double def) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return def;
        }
    }

    public static boolean get(ForgeConfigSpec.BooleanValue v, boolean def) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return def;
        }
    }
}
