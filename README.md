# Workers

Minecraft Forge mod for **1.20.1** (Java 17).

The long-term goal is a full population of working inhabitants. This first step
adds the entity everything else will be built on: the **Citizen**.

## Current state

- `workers:citizen` — a player-shaped passive mob.
  - Male citizens use the wide (Steve) player model and skin, female citizens the
    slim (Alex) one. Both textures are vanilla, so no skins ship with the mod yet.
  - Gender is rolled at spawn, synced to the client, and saved to NBT.
  - Movement only: wanders, looks at nearby players, idles, and floats instead of
    drowning. Full ground pathfinding. How a Citizen reacts to being hurt, to other
    mobs or to the world is deliberately left for later.
  - Spawned with `workers:citizen_spawn_egg` (Spawn Eggs creative tab). No natural
    spawning yet.

## Building and running

```bash
./gradlew build        # build the jar
./gradlew runClient    # launch the dev client
```

Requires a JDK 17 toolchain. If your default `java` is not 17:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew runClient
```

## Layout

```
src/main/java/com/qirick/workers/
├── Workers.java                     mod entry point
├── entity/
│   ├── ModEntities.java             entity type + attribute registration
│   └── citizen/
│       ├── CitizenEntity.java       the mob: AI, gender, NBT
│       └── CitizenGender.java       male/female → model variant + skin
├── item/ModItems.java               spawn egg
└── client/
    ├── ClientEvents.java            model layer + renderer registration
    ├── ModModelLayers.java          wide/slim model layer locations
    └── renderer/CitizenRenderer.java
```
