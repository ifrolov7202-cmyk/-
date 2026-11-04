package com.bamartial;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Настройки: config/ba_martialarts-common.toml
 */
public final class MartialConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue FINISHER_EVERY;
    public static final ForgeConfigSpec.IntValue UI_DURATION;
    public static final ForgeConfigSpec.DoubleValue UI_DODGE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue UI_AUTO_TRIGGER;
    public static final ForgeConfigSpec.DoubleValue UI_AUTO_HEALTH;
    public static final ForgeConfigSpec.IntValue UI_AUTO_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue BOOKS_IN_LOOT;
    public static final ForgeConfigSpec.BooleanValue BOOKS_AT_TRADER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("combat");
        DAMAGE_MULTIPLIER = b.comment("Множитель урона техник и добиваний / Damage multiplier for techniques and finishers")
                .defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        FINISHER_EVERY = b.comment("Каждый какой удар подряд — добивание стиля / Every Nth consecutive hit is a style finisher")
                .defineInRange("finisherEvery", 3, 2, 10);
        b.pop();
        b.push("ultra_instinct");
        UI_DURATION = b.comment("Длительность Ультра инстинкта, секунд / Ultra Instinct duration, seconds")
                .defineInRange("durationSeconds", 15, 3, 120);
        UI_DODGE_CHANCE = b.comment("Шанс автоуклонения от атаки / Auto-dodge chance")
                .defineInRange("dodgeChance", 0.75, 0.0, 1.0);
        UI_AUTO_TRIGGER = b.comment("Включается сам на грани поражения при полной шкале / Triggers by itself near death with a full meter")
                .define("autoTrigger", true);
        UI_AUTO_HEALTH = b.comment("Порог здоровья для авто-включения (доля от максимума) / Health threshold for auto trigger")
                .defineInRange("autoTriggerHealth", 0.2, 0.05, 0.9);
        UI_AUTO_COOLDOWN = b.comment("Перезарядка авто-включения, секунд / Auto trigger cooldown, seconds")
                .defineInRange("autoTriggerCooldownSeconds", 300, 0, 3600);
        b.pop();
        b.push("world");
        BOOKS_IN_LOOT = b.comment("Книги стилей в сундуках / Style books in chests").define("booksInLoot", true);
        BOOKS_AT_TRADER = b.comment("Книги стилей у странствующего торговца / Style books at the wandering trader").define("booksAtTrader", true);
        b.pop();
        SPEC = b.build();
    }

    private MartialConfig() {}

    public static double get(ForgeConfigSpec.DoubleValue v, double def) {
        try { return v.get(); } catch (IllegalStateException e) { return def; }
    }

    public static int get(ForgeConfigSpec.IntValue v, int def) {
        try { return v.get(); } catch (IllegalStateException e) { return def; }
    }

    public static boolean get(ForgeConfigSpec.BooleanValue v, boolean def) {
        try { return v.get(); } catch (IllegalStateException e) { return def; }
    }
}
