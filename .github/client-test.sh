#!/usr/bin/env bash
# Real game client against the running smoke server: Xvfb + software OpenGL.
# Usage (from the server dir): client-test.sh <out dir> <server console fifo>
# Writes CLIENT OK / CLIENT FAILED to <out>/result.txt, screenshots and client logs to <out>.
set -u
OUT=$(cd "$1" && pwd)
CONSOLE=$2
FV=$(cat "$OUT/forge_version.txt")
C=$(cd .. && pwd)/client
say() { echo "$*" > "$CONSOLE"; }
shot() { import -window root "$OUT/client-$1.png" 2>/dev/null || true; }
result() { echo "$1" >> "$OUT/result.txt"; echo "$1"; }

sudo apt-get update -qq >/dev/null 2>&1
sudo apt-get install -y -qq xvfb xdotool imagemagick libgl1-mesa-dri libgl1 libglx-mesa0 libxrandr2 libxi6 libxcursor1 libxxf86vm1 libxtst6 >/dev/null 2>&1
pip install -q --break-system-packages portablemc >/dev/null 2>&1 || pip install -q portablemc

Xvfb :99 -screen 0 1280x720x24 >/dev/null 2>&1 &
export DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5
sleep 2

mkdir -p "$C/mods"
cp mods/*.jar "$C/mods/"
rm -f "$C/mods/ba_martialarts_selftest.jar"
cat > "$C/options.txt" <<'EOF'
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
renderDistance:4
simulationDistance:5
maxFps:30
graphicsMode:0
pauseOnLostFocus:false
narrator:0
soundCategory_master:0.0
EOF
ls "$C/mods" > "$OUT/client-mods.txt"

portablemc start --main-dir "$C" "forge::1.20.1-$FV" -u Tester --join-server 127.0.0.1 --resolution 1280x720 \
  --jvm-arg=-Xmx4G > "$OUT/client.out" 2>&1 &
PMC=$!

joined=0
for i in $(seq 1 150); do
  if grep -q 'Tester joined the game' logs/latest.log 2>/dev/null; then joined=1; break; fi
  if ! kill -0 $PMC 2>/dev/null; then break; fi
  if ls "$C"/crash-reports/*.txt >/dev/null 2>&1; then break; fi
  [ $((i % 12)) -eq 0 ] && shot "loading-$i"
  sleep 5
done
echo "joined=$joined after ~$((i * 5)) s" | tee -a "$OUT/client-steps.txt"

if [ $joined -eq 1 ]; then
  sleep 25
  shot 1-joined
  WIN=$(xdotool search --name 'Minecraft' 2>/dev/null | head -1)
  [ -n "$WIN" ] && xdotool windowfocus "$WIN" 2>/dev/null
  say "gamemode creative Tester"; say "time set day"; say "weather clear"
  say "execute at Tester run summon annoyingvillagers:blue_villager_knight ^ ^ ^5"
  say "execute at Tester run summon annoyingvillagers:villager_scout ^2 ^ ^6"
  say "execute at Tester run summon annoyingvillagers:low_herobrine_clone ^-2 ^ ^6"
  say "execute at Tester run summon minecraft:husk ^ ^ ^3 {NoAI:1b,Health:200f,Attributes:[{Name:\"generic.max_health\",Base:200d}]}"
  sleep 10
  shot 2-mobs
  xdotool key F5 2>/dev/null; sleep 4
  shot 3-third-person
  xdotool key r 2>/dev/null; sleep 3          # Epic Fight battle mode
  xdotool mousedown 1 2>/dev/null; sleep 4; xdotool mouseup 1 2>/dev/null   # hold attack (rush)
  sleep 1
  shot 4-attack
  for k in 1 2 3; do xdotool click 1 2>/dev/null; sleep 0.6; done
  sleep 3
  shot 5-combo
  sleep 20
  shot 6-after
fi

alive=0; kill -0 $PMC 2>/dev/null && alive=1
crash=0; ls "$C"/crash-reports/*.txt >/dev/null 2>&1 && crash=1
grep -q 'Game crashed\|---- Minecraft Crash Report ----' "$C/logs/latest.log" "$OUT/client.out" 2>/dev/null && crash=1
echo "alive=$alive crash=$crash" | tee -a "$OUT/client-steps.txt"

cp "$C/logs/latest.log" "$OUT/client-latest.log" 2>/dev/null || true
grep -n "ERROR\|Exception\|Caused by\|Mixin.*fail\|InjectionError" "$C/logs/debug.log" 2>/dev/null | head -400 > "$OUT/client-errors.txt" || true
mkdir -p "$OUT/client-crash-reports"; cp "$C"/crash-reports/*.txt "$OUT/client-crash-reports/" 2>/dev/null || true

kill $PMC 2>/dev/null; pkill -f -- '--username Tester' 2>/dev/null || true
if [ $joined -eq 1 ] && [ $alive -eq 1 ] && [ $crash -eq 0 ]; then result "CLIENT OK"; else result "CLIENT FAILED joined=$joined alive=$alive crash=$crash"; fi
