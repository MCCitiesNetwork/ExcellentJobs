#!/usr/bin/env bash
# The optional 1.x integrations no longer publish all their old Maven artifacts.
# Install their official, checksum-pinned release JARs into the selected Maven cache.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .context/dependencies
maven_bin="${MVN:-mvn}"
install_release() {
  local filename="$1" url="$2" checksum="$3" group="$4" artifact="$5" version="$6"
  local destination=".context/dependencies/$filename"
  if [[ ! -f "$destination" ]]; then curl -fL --retry 2 "$url" -o "$destination"; fi
  printf '%s  %s\n' "$checksum" "$destination" | sha256sum --check
  "$maven_bin" -B -ntp install:install-file \
    "-Dfile=$destination" "-DgroupId=$group" "-DartifactId=$artifact" \
    "-Dversion=$version" -Dpackaging=jar -DgeneratePom=true
}

install_release LevelledMobs-4.0.5.jar \
  https://github.com/ArcanePlugins/LevelledMobs/releases/download/4.0.5/LevelledMobs-4.0.5.b29.jar \
  f5b21f475ae3b932d6b0a419147dd1975d128503167536e84e0392a0c9e88df2 \
  io.github.arcaneplugins.levelledmobs LevelledMobs 4.0.5
install_release EvenMoreFish-2.0.0.jar \
  https://github.com/EvenMoreFish/EvenMoreFish/releases/download/v2.0.0/even-more-fish-2.0.0.jar \
  4a0e3dca8df766b19f6d0b79558818ffc266618c94d35082a4390e306fc7996c \
  com.oheers.fish EvenMoreFish 2.0.0
install_release ProtocolLib-5.3.0.jar \
  https://github.com/dmulloy2/ProtocolLib/releases/download/5.3.0/ProtocolLib.jar \
  a91028876b974f870e1ac90cb433a1a4cb06930a39f2c0edc50d6165e26e940d \
  com.comphenix.protocol ProtocolLib 5.3.0
