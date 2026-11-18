"""Правки EpicFight: Annoying Villagers 1.5.2 для Epic Fight 20.14.16 (изменённая сборка, GPL-3).

Запуск: python3 patch.py <папка с исходниками EpicFight_AnnoyingVillagers на коммите 5aea1e4>
"""
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
changes = []


def sub(rel, old, new, count=1):
    p = root / rel
    s = p.read_text(encoding='utf-8')
    if s.count(old) != count:
        sys.exit(f"{rel}: expected {count} x {old!r}, found {s.count(old)}")
    p.write_text(s.replace(old, new), encoding='utf-8')
    changes.append(f"{rel}: {old.strip()[:70]!r} -> {new.strip()[:70]!r}")


# --- сборка: Epic Fight 20.14.16 вместо 20.14.17, jar в build/libs
sub('build.gradle', 'implementation fg.deobf("curse.maven:epic-fight-mod-405076:8049910")',
    'implementation fg.deobf("maven.modrinth:epic-fight:20.14.16-mc1.20.1-forge")')
sub('build.gradle', """    maven {
        url = uri("https://cursemaven.com")
    }""", """    maven {
        url = uri("https://cursemaven.com")
    }
    maven {
        url = uri("https://api.modrinth.com/maven")
        content { includeGroup "maven.modrinth" }
    }""")
sub('build.gradle', '    destinationDirectory = file("C:/Users/Admin/Desktop/Minecraft/delivery")\n', '')
sub('gradle.properties', 'mod_version=1.5.2', 'mod_version=1.5.2-ef20.14.16')
sub('gradle.properties', 'mod_description=Compatibility between Annoying Villagers and EpicFight mod.',
    'mod_description=Compatibility between Annoying Villagers and EpicFight mod. Modified build: backported to Epic Fight 20.14.16 (not by the original author).')

# --- в Epic Fight 20.14.16 лямбда addDefaultItems с двумя вызовами пресета называется lambda$addDefaultItems$10
sub('src/main/java/com/pla/epicfight_annoyingvillagers/mixin/ItemCapabilityProviderPresetMixin.java',
    'method = {"get", "lambda$addDefaultItems$3"}', 'method = {"get", "lambda$addDefaultItems$10"}')

# --- в 20.14.16 лук и арбалет — одна категория RANGED
import re
java = root / 'src/main/java'
for f in java.rglob('*.java'):
    t = f.read_text(encoding='utf-8')
    t2 = re.sub(r'WeaponCategories\.(BOW|CROSSBOW)\b', 'WeaponCategories.RANGED', t)
    # --- в 20.14.16 нет констант WeaponCapabilityPresets.SWORD и т.п.: набор узнаём по имени в реестре
    t2 = re.sub(r'preset == WeaponCapabilityPresets\.([A-Z_]+)\b',
                lambda m: 'com.pla.epicfight_annoyingvillagers.capabilities.Ef16Presets.is(preset, "' + m.group(1).lower() + '")', t2)
    if t2 != t:
        f.write_text(t2, encoding='utf-8')
        changes.append(f"{f.relative_to(root)}: BOW/CROSSBOW -> RANGED, WeaponCapabilityPresets.X -> Ef16Presets.is(preset, x)")

(java / 'com/pla/epicfight_annoyingvillagers/capabilities/Ef16Presets.java').write_text("""package com.pla.epicfight_annoyingvillagers.capabilities;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponTypeReloadListener;
import yesman.epicfight.world.capabilities.provider.ItemCapabilityProvider;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Function;

/**
 * Backport to Epic Fight 20.14.16 (modified build): 20.14.16 has no WeaponCapabilityPresets.SWORD-style constants.
 * A built-in preset is recognised by its entry in WeaponTypeReloadListener ("epicfight:sword", ...) or, for vanilla
 * tool classes, by the factory that ItemCapabilityProvider keeps for that class.
 */
public final class Ef16Presets {
    private static final Map<String, Class<? extends Item>> VANILLA = Map.of(
            "sword", SwordItem.class, "axe", AxeItem.class, "pickaxe", PickaxeItem.class,
            "shovel", ShovelItem.class, "hoe", HoeItem.class);
    private static Map<Class<?>, Function<Item, CapabilityItem.Builder>> byClass;
    private static boolean byClassFailed;

    private Ef16Presets() {
    }

    public static boolean is(Function<Item, CapabilityItem.Builder> preset, String name) {
        if (preset == null) return false;
        try {
            if (preset == WeaponTypeReloadListener.get(new ResourceLocation("epicfight", name))) return true;
        } catch (RuntimeException ignored) {
        }
        Class<? extends Item> vanilla = VANILLA.get(name);
        if (vanilla == null) return false;
        Map<Class<?>, Function<Item, CapabilityItem.Builder>> map = byClass();
        return map != null && preset == map.get(vanilla);
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Function<Item, CapabilityItem.Builder>> byClass() {
        if (byClass == null && !byClassFailed) {
            try {
                Field f = ItemCapabilityProvider.class.getDeclaredField("CAPABILITY_BY_CLASS");
                f.setAccessible(true);
                byClass = (Map<Class<?>, Function<Item, CapabilityItem.Builder>>) f.get(null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                byClassFailed = true;
            }
        }
        return byClass;
    }
}
""", encoding='utf-8')
changes.append("added capabilities/Ef16Presets.java")

(root / 'BACKPORT_CHANGES.md').write_text(
    "# Modified build\n\nEpicFight: Annoying Villagers 1.5.2 (PlaIsMe/EpicFight_AnnoyingVillagers @ 5aea1e4), "
    "changed to run on Epic Fight 20.14.16. Licensed under GPL-3 like the original.\n\n"
    + "\n".join("- " + c for c in changes) + "\n", encoding='utf-8')
print("\n".join(changes))
