# DeathDrop plugin

DeathDrop gives each player a protected Map Selector Nether Star and Checkpoint Compass.
The star opens a GUI containing every configured map. Selecting a map teleports the player to its checkpoint and starts a map session. Any death during that session respawns the player at the checkpoint. The compass returns the player to the checkpoint of the last selected map.

## Build

The project targets Paper/Minecraft 26.3 and Java 25. The server does not currently include a Gradle wrapper, so install Gradle or use the cached Paper API when compiling manually.

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
