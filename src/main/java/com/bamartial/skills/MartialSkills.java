package com.bamartial.skills;

import com.bamartial.BAMartial;
import net.forixaim.battle_arts_api.battle_arts_skills.BattleArtsSkillSlots;
import net.forixaim.battle_arts_api.battle_arts_skills.CoreAPIDataKeys;
import net.forixaim.battle_arts_api.battle_arts_skills.active.burst_arts.BurstArt;
import net.forixaim.battle_arts_api.battle_arts_skills.active.ultimate_arts.UltimateArt;
import net.minecraft.world.entity.player.Player;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
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
    /** Абсолютное искусство всех стилей (клавиша Battle Arts V). */
    public static Skill ULTRA_INSTINCT;

    private MartialSkills() {}

    /** Контейнер слота стиля боя, если на игроке надет наш рукопашный стиль; иначе null. */
    public static SkillContainer styleContainer(Player player) {
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        if (patch == null) return null;
        SkillContainer container = patch.getSkill(BattleArtsSkillSlots.BATTLE_STYLE);
        return container != null && container.getSkill() instanceof MartialBattleStyle ? container : null;
    }

    /** Наш рукопашный стиль на игроке или null. */
    public static MartialBattleStyle style(Player player) {
        SkillContainer c = styleContainer(player);
        return c == null ? null : (MartialBattleStyle) c.getSkill();
    }

    /** Сколько метра Battle Arts накоплено (100 = одно деление). */
    public static float meter(SkillContainer styleContainer) {
        if (styleContainer == null || !styleContainer.getDataManager().hasData(CoreAPIDataKeys.METER_FILL.get())) return 0;
        return styleContainer.getDataManager().getDataValue(CoreAPIDataKeys.METER_FILL.get());
    }

    public static void onSkillBuild(SkillBuildEvent event) {
        SkillBuildEvent.ModRegistryWorker worker = event.createRegistryWorker(BAMartial.MODID);

        SkillBuilder<BurstArt> burst = BurstArt.createBurstArt();
        burst.setResource(Skill.Resource.NONE);
        FLURRY = worker.build("flurry_of_blows", FlurryBurstArt::new, burst);

        SkillBuilder<UltimateArt> ultimate = UltimateArt.createUltimateArt();
        ultimate.setResource(Skill.Resource.NONE);
        ULTRA_INSTINCT = worker.build("ultra_instinct", UltraInstinctArt::new, ultimate);

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
