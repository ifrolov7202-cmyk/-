package com.martialarts.skill;

import com.martialarts.MartialArts;
import com.martialarts.data.MartialData;
import com.martialarts.data.MartialStyle;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Выдача достижений мода (data/martialarts/advancements/martial/*.json).
 */
public final class MartialAdvancements {
    private MartialAdvancements() {}

    public static void award(ServerPlayer p, String id) {
        if (p.getServer() == null) return;
        Advancement adv = p.getServer().getAdvancements()
                .getAdvancement(new ResourceLocation(MartialArts.MODID, "martial/" + id));
        if (adv == null) return;
        AdvancementProgress progress = p.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) return;
        List<String> remaining = new ArrayList<>();
        for (String c : progress.getRemainingCriteria()) remaining.add(c);
        for (String c : remaining) p.getAdvancements().award(adv, c);
    }

    /** Проверить достижения за уровень и стили. */
    public static void check(ServerPlayer p, MartialData d) {
        if (d.level >= 5) award(p, "level_5");
        if (d.level >= 10) award(p, "level_10");
        if (d.level >= 20) award(p, "level_20");
        boolean any = false;
        boolean all = true;
        for (MartialStyle s : MartialStyle.values()) {
            if (s == MartialStyle.NONE) continue;
            if (d.isLearned(s)) any = true;
            else all = false;
        }
        if (any) award(p, "first_style");
        if (all) award(p, "all_styles");
    }
}
