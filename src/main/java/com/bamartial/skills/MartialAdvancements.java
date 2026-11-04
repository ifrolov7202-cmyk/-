package com.bamartial.skills;

import com.bamartial.BAMartial;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Выдача достижений (data/ba_martialarts/advancements/martial/*.json).
 */
public final class MartialAdvancements {
    private MartialAdvancements() {}

    public static void award(ServerPlayer p, String id) {
        if (p == null || p.getServer() == null) return;
        Advancement adv = p.getServer().getAdvancements().getAdvancement(new ResourceLocation(BAMartial.MODID, "martial/" + id));
        if (adv == null) return;
        AdvancementProgress progress = p.getAdvancements().getOrStartProgress(adv);
        if (progress.isDone()) return;
        List<String> remaining = new ArrayList<>();
        for (String c : progress.getRemainingCriteria()) remaining.add(c);
        for (String c : remaining) p.getAdvancements().award(adv, c);
    }
}
