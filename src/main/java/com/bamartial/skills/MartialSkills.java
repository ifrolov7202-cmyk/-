package com.bamartial.skills;

import com.bamartial.BAMartial;
import net.forixaim.battle_arts_api.battle_arts_skills.active.burst_arts.BurstArt;
import net.forixaim.battle_arts_api.battle_arts_skills.active.combat_arts.CombatArt;
import net.forixaim.battle_arts_api.battle_arts_skills.battle_style.BattleStyle;
import net.forixaim.battle_arts_api.battle_arts_skills.battle_style.BattleStyleCategories;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillBuilder;

import java.util.EnumMap;
import java.util.Map;

/**
 * Регистрация навыков в Epic Fight.
 */
public final class MartialSkills {
    public static final Map<StyleKind, Skill> STYLES = new EnumMap<>(StyleKind.class);
    public static final Map<StyleKind, Skill> COMBAT_ARTS = new EnumMap<>(StyleKind.class);
    /** Взрывное искусство всех стилей (клавиша Battle Arts C). */
    public static Skill FLURRY;

    private MartialSkills() {}

    public static void onSkillBuild(SkillBuildEvent event) {
        SkillBuildEvent.ModRegistryWorker worker = event.createRegistryWorker(BAMartial.MODID);

        SkillBuilder<BurstArt> burst = BurstArt.createBurstArt();
        burst.setResource(Skill.Resource.NONE);
        FLURRY = worker.build("flurry_of_blows", FlurryBurstArt::new, burst);

        for (StyleKind kind : StyleKind.values()) {
            SkillBuilder<CombatArt> art = CombatArt.createCombatArt();
            art.setResource(Skill.Resource.NONE);
            COMBAT_ARTS.put(kind, worker.build(kind.artId, b -> new MartialCombatArt(b, kind), art));

            BattleStyle.Builder<BattleStyle> style = BattleStyle.createBattleStyleBuilder();
            style.setBattleStyleCategory(BattleStyleCategories.NOVICE);
            STYLES.put(kind, worker.build(kind.styleId, b -> new MartialBattleStyle(b, kind), style));
        }
    }
}
