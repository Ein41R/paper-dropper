# DeathDrop plugin

DeathDrop gives each player a protected Map Selector Nether Star and Checkpoint Compass.
The star opens a GUI containing every configured map. Selecting a map teleports the player to its checkpoint and starts a map session. Any death during that session respawns the player at the checkpoint. The compass returns the player to the checkpoint of the last selected map.

## Build and install

The plugin targets Paper/Minecraft 26.3 and Java 25. Build it from the server root, the directory containing `build.gradle.kts`.

### Prerequisites

- Java 25 JDK, including `javac`.
- The bundled Paper API libraries under `libraries/`.

Build through the included Gradle-style wrapper:

```sh
./gradlew build
```

The wrapper uses the local Java 25 compiler and bundled libraries, so a separate
Gradle installation is not required. `./gradlew clean` removes generated class
and build output.

### Compile the plugin

From the server root, run:

```sh
./gradlew build
```

The build compiles `src/main/java`, copies `src/main/resources`, and creates the
runtime plugin directly at `plugins/DeathDrop.jar`.

### Restart with the built JAR

Stop the Paper server before replacing the plugin JAR. Then copy the newly built artifact into `plugins/` and give it the stable filename used by this server:

```sh
The JAR is already in the correct location after `./gradlew build`.

Start the server from the server root:

```sh
./run.sh
```

Or run the Paper command directly:

```sh
java -Xms2G -Xmx4G -jar paper-26.3-205.jar --nogui
```

Confirm that the console reports `DeathDrop` enabled. The plugin configuration is then available at `plugins/DeathDrop/config.yml`; reload it after configuration changes with:

```text
/dd reload
```

### Quick rebuild loop

After changing source code, stop the server, rebuild, replace the JAR, and start the server again:

```sh
./gradlew clean
./gradlew build
./run.sh
```

Do not replace the JAR while the server is running. Paper may keep the old classes loaded until the next restart.

## Moderator setup

Run these commands as an operator:

```text
/dd create canyon "Canyon Drop"
/dd setcheckpoint canyon
/dd setfinish canyon pressure
/dd sethomingroom
/dd reload
```

For a command-block finish, put a pressure plate on top of a command block and set its command to:

```text
dd finish canyon
```

The command completes the nearest player within six blocks who is currently playing that map. You can also target a specific player with `dd finish canyon <player>`.

Stand at the desired location before running each location command. `setfinish` accepts `pressure`, `sign`, or `both`. For a pressure plate, stand on the block that should trigger completion. For a sign, stand at the sign block and use `sign`.

Useful commands:

```text
/dd list
/dd delete <map>
/dd setitem <map> <material>
/dd give [player]
/dd reload
```

Use `/dd setitem sports DIAMOND` to choose the item shown for a map in the selector. The command saves to the live plugin configuration and reloads the map data immediately. Materials must be item materials, such as `PAPER`, `DIAMOND`, `FEATHER`, or `NETHER_STAR`.

Map definitions are stored in `plugins/DeathDrop/config.yml`. Player session data is stored in `plugins/DeathDrop/players.yml`.

## Item styling

The same `config.yml` controls the item names, lore, and selector GUI icons. Names and lore support Minecraft color codes using `&`:

```yaml
items:
	selector-name: "&b&lMap Selector"
	compass-name: "&a&lCheckpoint Compass"
menu:
	item-material: DIAMOND
```

Maps can choose a different selector item by setting `item-material` inside the map:

```yaml
maps:
	canyon:
		display-name: "Canyon Drop"
		item-material: DIAMOND
```

Maps without this setting use `menu.item-material` as the fallback.

The Nether Star and Compass have an enchantment glint automatically. The enchantment is hidden, so it is only used for the visual effect.

Completion pauses for three seconds by default, showing the finish title before teleporting. The delay and both title screens can be changed under `completion` in `config.yml`.
