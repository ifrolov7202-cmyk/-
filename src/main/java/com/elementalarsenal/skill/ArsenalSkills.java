package com.elementalarsenal.skill;

import com.elementalarsenal.ElementalArsenal;
import com.elementalarsenal.Element;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.weaponinnate.WeaponInnateSkill;

import java.util.EnumMap;
import java.util.Map;

/** Регистрация врождённых навыков оружия в Epic Fight. */
public final class ArsenalSkills {
    private static final Map<Element, Skill> SKILLS = new EnumMap<>(Element.class);

    private ArsenalSkills() {}

    public static Skill get(Element element) {
        return SKILLS.get(element);
    }

    public static void onSkillBuild(SkillBuildEvent event) {
        SkillBuildEvent.ModRegistryWorker worker = event.createRegistryWorker(ElementalArsenal.MODID);
        for (Element element : Element.values()) {
            SKILLS.put(element, worker.build(element.skillId, b -> new ElementalArtSkill(b, element),
                    WeaponInnateSkill.createWeaponInnateBuilder()));
        }
    }
}
