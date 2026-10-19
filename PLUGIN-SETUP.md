# DeathDrop plugin

DeathDrop gives each player a protected Map Selector Nether Star and Checkpoint Compass.
The star opens a GUI containing every configured map. Selecting a map teleports the player to its checkpoint and starts a map session. Any death during that session respawns the player at the checkpoint. The compass returns the player to the checkpoint of the last selected map.

## Build and install

The plugin targets Paper/Minecraft 26.3 and Java 25. Build it from the server root, the directory containing `build.gradle.kts`.

### Prerequisites

- Java 25 JDK, including `javac`.
- Gradle 9.x or another Gradle release that supports Java 25.
- Internet access the first time you build, so Gradle can resolve the Paper API dependency.

This repository does not include a Gradle wrapper. Check the tools before building:

```sh
java -version
gradle --version
```

On Debian or Ubuntu, install the JDK with:

```sh
sudo apt update
sudo apt install openjdk-25-jdk
```

Install Gradle using your preferred system package manager or the official Gradle installation instructions. Confirm that `gradle --version` reports a Java 25-compatible Gradle version.

### Compile the plugin

From `/home/SIBYL/Dev/server` (or the directory where this repository is checked out), run:

```sh
gradle clean build
```

The build compiles `src/main/java`, copies `src/main/resources`, and runs the verification tasks. A successful build ends with `BUILD SUCCESSFUL` and creates a JAR under `build/libs/`. The filename normally includes the project directory and version, for example:

```text
build/libs/server-1.0.0.jar
```

Use the actual filename shown by this command if the directory or version has changed:

```sh
ls -lh build/libs/*.jar
```

### Install the built JAR

Stop the Paper server before replacing the plugin JAR. Then copy the newly built artifact into `plugins/` and give it the stable filename used by this server:

```sh
cp build/libs/*.jar plugins/DeathDrop.jar
```

If more than one JAR exists in `build/libs/`, replace the wildcard with the exact artifact filename. Do not copy the `-plain.jar` artifact if Gradle creates one; use the regular JAR.

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
gradle clean build
artifact="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)"
cp "$artifact" plugins/DeathDrop.jar
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
/dd give [player]
/dd reload
```

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

The Nether Star and Compass have an enchantment glint automatically. The enchantment is hidden, so it is only used for the visual effect.

Completion pauses for three seconds by default, showing the finish title before teleporting. The delay and both title screens can be changed under `completion` in `config.yml`.
