# Debris Guide (Fabric, Minecraft 1.21.11, client-side)

Highlights ancient debris through walls (orange), marks the selected target (green) and draws the
cheapest tunnel route to it (cyan). Keys: G = toggle, H = cycle to the next-best debris.

## Build
Requires JDK 21 and internet access (downloads Minecraft + Fabric).

    gradle wrapper --gradle-version 9.2.1     # once, if there is no ./gradlew
    ./gradlew build                           # Windows: gradlew.bat build

Jar: build/libs/oreguide-1.0.0.jar -> put in .minecraft/mods with Fabric Loader + Fabric API.
Dev run: ./gradlew runClient

If a version fails to resolve, generate a fresh 1.21.11 template at https://fabricmc.net/develop/
and copy its versions into gradle.properties.
