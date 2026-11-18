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

(root / 'BACKPORT_CHANGES.md').write_text(
    "# Modified build\n\nEpicFight: Annoying Villagers 1.5.2 (PlaIsMe/EpicFight_AnnoyingVillagers @ 5aea1e4), "
    "changed to run on Epic Fight 20.14.16. Licensed under GPL-3 like the original.\n\n"
    + "\n".join("- " + c for c in changes) + "\n", encoding='utf-8')
print("\n".join(changes))
