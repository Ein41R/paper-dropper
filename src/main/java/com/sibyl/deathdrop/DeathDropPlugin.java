package com.sibyl.deathdrop;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

public final class DeathDropPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String MENU_TITLE = ChatColor.DARK_AQUA + "DeathDrop Maps";
    private final Map<String, MapInfo> maps = new LinkedHashMap<>();
    private final Map<UUID, PlayerState> states = new LinkedHashMap<>();
    private NamespacedKey starKey;
    private NamespacedKey compassKey;
    private File stateFile;
    private FileConfiguration stateConfig;
    private Location homingRoom;

    @Override
    public void onEnable() {
        starKey = new NamespacedKey(this, "map_selector");
        compassKey = new NamespacedKey(this, "checkpoint_compass");
        saveDefaultConfig();
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("dd").setExecutor(this);
        getCommand("dd").setTabCompleter(this);
        getLogger().info("DeathDrop enabled with " + maps.size() + " map(s).");
    }

    @Override
    public void onDisable() {
        savePlayerStates();
    }

    private void loadData() {
        maps.clear();
        ConfigurationSection mapSection = getConfig().getConfigurationSection("maps");
        if (mapSection != null) {
            for (String id : mapSection.getKeys(false)) {
                ConfigurationSection section = mapSection.getConfigurationSection(id);
                Location checkpoint = readLocation(section, "checkpoint");
                Location finish = readLocation(section, "finish.location");
                if (checkpoint != null) {
                    maps.put(id.toLowerCase(), new MapInfo(
                            id.toLowerCase(),
                            section.getString("display-name", id),
                            menuItemMaterial(section.getString("item-material")),
                            checkpoint,
                            finish,
                            FinishType.from(section.getString("finish.type", "BOTH"))));
                }
            }
        }
        homingRoom = readLocation(getConfig(), "homing-room");
        stateFile = new File(getDataFolder(), "players.yml");
        stateConfig = YamlConfiguration.loadConfiguration(stateFile);
        states.clear();
        ConfigurationSection playerSection = stateConfig.getConfigurationSection("players");
        if (playerSection != null) {
            for (String rawUuid : playerSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    states.put(uuid, new PlayerState(
                            playerSection.getString(rawUuid + ".active-map"),
                            playerSection.getString(rawUuid + ".last-map")));
                } catch (IllegalArgumentException ignored) {
                    getLogger().warning("Ignoring invalid player UUID in players.yml: " + rawUuid);
                }
            }
        }
    }

    private void savePlayerStates() {
        if (stateConfig == null) {
            return;
        }
        stateConfig.set("players", null);
        for (Map.Entry<UUID, PlayerState> entry : states.entrySet()) {
            String path = "players." + entry.getKey();
            stateConfig.set(path + ".active-map", entry.getValue().activeMap);
            stateConfig.set(path + ".last-map", entry.getValue().lastMap);
        }
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                getLogger().warning("Could not create plugin data folder.");
            }
            stateConfig.save(stateFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save players.yml: " + exception.getMessage());
        }
    }

    private void savePluginConfig() {
        try {
            saveConfig();
        } catch (Exception exception) {
            getLogger().severe("Could not save config.yml: " + exception.getMessage());
        }
    }

    private PlayerState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState(null, null));
    }

    private void giveItems(Player player) {
        if (!hasTaggedItem(player, starKey)) {
            player.getInventory().addItem(createStar());
        }
        if (!hasTaggedItem(player, compassKey)) {
            player.getInventory().addItem(createCompass());
        }
    }

    private boolean hasTaggedItem(Player player, NamespacedKey key) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTagged(item, key)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createStar() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("items.selector-name", "&b&lMap Selector")));
        meta.setLore(List.of(color(getConfig().getString("items.selector-lore", "&7Right-click to choose a map."))));
        addGlint(meta);
        meta.getPersistentDataContainer().set(starKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("items.compass-name", "&a&lCheckpoint Compass")));
        meta.setLore(List.of(color(getConfig().getString("items.compass-lore", "&7Right-click to return to your last checkpoint."))));
        addGlint(meta);
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private void addGlint(ItemMeta meta) {
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private Material menuItemMaterial(String configuredMaterial) {
        String materialName = configuredMaterial != null
                ? configuredMaterial
                : getConfig().getString("menu.item-material", "PAPER");
        Material material = Material.matchMaterial(materialName);
        return material == null || !material.isItem() ? Material.PAPER : material;
    }

    private boolean isTagged(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return data.has(key, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        giveItems(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (isTagged(stack, starKey) || isTagged(stack, compassKey)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "These items cannot be dropped.");
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) {
            ItemStack stack = event.getItem().getItemStack();
            if (isTagged(stack, starKey) || isTagged(stack, compassKey)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        ItemStack item = event.getItem();
        if (isTagged(item, starKey)) {
            event.setCancelled(true);
            openMapMenu(event.getPlayer());
        } else if (isTagged(item, compassKey)) {
            event.setCancelled(true);
            teleportToLastCheckpoint(event.getPlayer());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType().name().contains("SIGN")) {
            completeFromSign(event.getPlayer(), event.getClickedBlock().getLocation());
        }
    }

    private void openMapMenu(Player player) {
        List<MapInfo> available = new ArrayList<>(maps.values());
        int size = Math.max(9, Math.min(54, ((available.size() + 8) / 9) * 9));
        MapMenuHolder holder = new MapMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, MENU_TITLE);
        holder.inventory = inventory;
        for (int index = 0; index < available.size() && index < size; index++) {
            MapInfo map = available.get(index);
            ItemStack icon = new ItemStack(map.itemMaterial);
            ItemMeta meta = icon.getItemMeta();
            String itemName = getConfig().getString("menu.item-name", "&6&l%map%");
            String itemLore = getConfig().getString("menu.item-lore", "&7Click to enter this map.");
            meta.setDisplayName(color(itemName.replace("%map%", map.displayName)));
            meta.setLore(List.of(color(itemLore.replace("%map%", map.displayName))));
            meta.getPersistentDataContainer().set(new NamespacedKey(this, "map_" + map.id), PersistentDataType.BYTE, (byte) 1);
            icon.setItemMeta(meta);
            inventory.setItem(index, icon);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MapMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        for (MapInfo map : maps.values()) {
            NamespacedKey key = new NamespacedKey(this, "map_" + map.id);
            if (isTagged(clicked, key)) {
                enterMap(player, map);
                return;
            }
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MapMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void enterMap(Player player, MapInfo map) {
        PlayerState playerState = state(player);
        playerState.activeMap = map.id;
        playerState.lastMap = map.id;
        savePlayerStates();
        player.closeInventory();
        player.teleport(map.checkpoint);
        player.sendMessage(ChatColor.AQUA + "Entering " + map.displayName + ".");
    }

    private void teleportToLastCheckpoint(Player player) {
        String lastMap = state(player).lastMap;
        MapInfo map = lastMap == null ? null : maps.get(lastMap);
        if (map == null) {
            player.sendMessage(ChatColor.RED + "You have not entered a map yet.");
            return;
        }
        player.teleport(map.checkpoint);
        player.sendMessage(ChatColor.GREEN + "Returned to the checkpoint for " + map.displayName + ".");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        PlayerState playerState = state(event.getEntity());
        if (playerState.activeMap != null && maps.containsKey(playerState.activeMap)) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        MapInfo map = activeMap(event.getPlayer());
        if (map != null) {
            event.setRespawnLocation(map.checkpoint);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        MapInfo map = activeMap(event.getPlayer());
        if (map != null && map.finishType.allowsPressure() && sameBlock(event.getTo(), map.finishLocation)) {
            completeMap(event.getPlayer(), map);
        }
    }

    private void completeFromSign(Player player, Location location) {
        MapInfo map = activeMap(player);
        if (map != null && map.finishType.allowsSign() && sameBlock(location, map.finishLocation)) {
            completeMap(player, map);
        }
    }

    private void completeMap(Player player, MapInfo map) {
        PlayerState playerState = state(player);
        playerState.activeMap = null;
        savePlayerStates();
        Location celebrationLocation = player.getLocation().clone().add(0, 1, 0);
        player.sendTitle(color(getConfig().getString("completion.finish-title", "&6&lMAP COMPLETE!")),
                color(getConfig().getString("completion.finish-subtitle", "&e%map%").replace("%map%", map.displayName)),
                10, 70, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, celebrationLocation, 80, 0.6, 0.9, 0.6, 0.15);
        player.getWorld().spawnParticle(Particle.END_ROD, celebrationLocation, 45, 0.8, 0.8, 0.8, 0.08);
        player.getWorld().spawnParticle(Particle.ENCHANT, celebrationLocation, 100, 0.8, 1.0, 0.8, 1.0);
        long delayTicks = Math.max(0, getConfig().getLong("completion.teleport-delay-seconds", 7) * 20);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (homingRoom != null) {
                player.teleport(homingRoom);
                player.sendTitle(color(getConfig().getString("completion.homing-title", "&b&lHOMING ROOM")),
                        color(getConfig().getString("completion.homing-subtitle", "&7Welcome back")), 10, 60, 20);
            }
            player.sendMessage(ChatColor.GREEN + "Map complete: " + map.displayName + "!");
        }, delayTicks);
    }

    private Player findNearbyActivePlayer(Location location, String mapId) {
        Player closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!mapId.equals(state(player).activeMap) || !player.getWorld().equals(location.getWorld())) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(location);
            if (distance <= 36 && distance < closestDistance) {
                closest = player;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private MapInfo activeMap(Player player) {
        String activeMap = state(player).activeMap;
        return activeMap == null ? null : maps.get(activeMap);
    }

    private boolean sameBlock(Location first, Location second) {
        return first != null && second != null && first.getWorld() != null && second.getWorld() != null
                && first.getWorld().equals(second.getWorld()) && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY() && first.getBlockZ() == second.getBlockZ();
    }

    private Location readLocation(ConfigurationSection section, String path) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("World not loaded for location " + path + ": " + worldName);
            return null;
        }
        return new Location(world, section.getDouble(path + ".x"), section.getDouble(path + ".y"),
                section.getDouble(path + ".z"), (float) section.getDouble(path + ".yaw"),
                (float) section.getDouble(path + ".pitch"));
    }

    private void writeLocation(String path, Location location) {
        getConfig().set(path + ".world", location.getWorld().getName());
        getConfig().set(path + ".x", location.getX());
        getConfig().set(path + ".y", location.getY());
        getConfig().set(path + ".z", location.getZ());
        getConfig().set(path + ".yaw", location.getYaw());
        getConfig().set(path + ".pitch", location.getPitch());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/dd create|delete|setcheckpoint|setfinish|setitem|sethomingroom|list|reload|give");
            return true;
        }
        String subcommand = args[0].toLowerCase();
        if (subcommand.equals("list")) {
            sender.sendMessage(ChatColor.AQUA + "Maps: " + (maps.isEmpty() ? "none" : String.join(", ", maps.keySet())));
            return true;
        }
        if (subcommand.equals("reload")) {
            reloadConfig();
            loadData();
            sender.sendMessage(ChatColor.GREEN + "DeathDrop configuration reloaded.");
            return true;
        }
        if (subcommand.equals("give")) {
            Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Usage: /dd give <player>");
                return true;
            }
            giveItems(target);
            sender.sendMessage(ChatColor.GREEN + "Items given to " + target.getName() + ".");
            return true;
        }
        if (subcommand.equals("setitem")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /dd setitem <map> <material>");
                return true;
            }
            String mapId = args[1].toLowerCase();
            if (!maps.containsKey(mapId)) {
                sender.sendMessage(ChatColor.RED + "Unknown map: " + mapId);
                return true;
            }
            Material material = Material.matchMaterial(args[2]);
            if (material == null || !material.isItem()) {
                sender.sendMessage(ChatColor.RED + "Invalid item material: " + args[2]);
                return true;
            }
            getConfig().set("maps." + mapId + ".item-material", material.name());
            savePluginConfig();
            loadData();
            sender.sendMessage(ChatColor.GREEN + "Set " + mapId + " selector item to " + material.name() + ".");
            return true;
        }
        if (subcommand.equals("finish") && args.length >= 2) {
            String mapId = args[1].toLowerCase();
            MapInfo map = maps.get(mapId);
            if (map == null) {
                sender.sendMessage(ChatColor.RED + "Unknown map: " + mapId);
                return true;
            }
            Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
            if (target == null && sender instanceof Player player) {
                target = player;
            }
            if (target == null && sender instanceof BlockCommandSender blockSender) {
                target = findNearbyActivePlayer(blockSender.getBlock().getLocation(), mapId);
            }
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "No active player found for map " + mapId + ".");
                return true;
            }
            if (!mapId.equals(state(target).activeMap)) {
                sender.sendMessage(ChatColor.RED + target.getName() + " is not currently playing " + mapId + ".");
                return true;
            }
            completeMap(target, map);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command requires a player for its location.");
            return true;
        }
        if (subcommand.equals("create") && args.length >= 2) {
            String id = args[1].toLowerCase();
            getConfig().set("maps." + id + ".display-name", args.length >= 3 ? args[2] : id);
            savePluginConfig();
            loadData();
            player.sendMessage(ChatColor.GREEN + "Created map " + id + ". Set its checkpoint and finish next.");
            return true;
        }
        if (subcommand.equals("delete") && args.length >= 2) {
            getConfig().set("maps." + args[1].toLowerCase(), null);
            savePluginConfig();
            loadData();
            player.sendMessage(ChatColor.GREEN + "Deleted map " + args[1].toLowerCase() + ".");
            return true;
        }
        if (subcommand.equals("setcheckpoint") && args.length >= 2) {
            String id = args[1].toLowerCase();
            writeLocation("maps." + id + ".checkpoint", player.getLocation());
            savePluginConfig();
            loadData();
            player.sendMessage(ChatColor.GREEN + "Checkpoint set for " + id + ".");
            return true;
        }
        if (subcommand.equals("setfinish") && args.length >= 2) {
            String id = args[1].toLowerCase();
            String type = args.length >= 3 ? args[2].toUpperCase() : "BOTH";
            if (!FinishType.isValid(type)) {
                player.sendMessage(ChatColor.RED + "Finish type must be PRESSURE, SIGN, or BOTH.");
                return true;
            }
            writeLocation("maps." + id + ".finish.location", player.getLocation());
            getConfig().set("maps." + id + ".finish.type", type);
            savePluginConfig();
            loadData();
            player.sendMessage(ChatColor.GREEN + "Finish set for " + id + " using " + type + ".");
            return true;
        }
        if (subcommand.equals("sethomingroom")) {
            writeLocation("homing-room", player.getLocation());
            savePluginConfig();
            loadData();
            player.sendMessage(ChatColor.GREEN + "Homing room set.");
            return true;
        }
        player.sendMessage(ChatColor.RED + "Usage: /dd create <id> [display-name], /dd setcheckpoint <id>, /dd setfinish <id> [pressure|sign|both], /dd setitem <map> <material>, /dd sethomingroom, /dd list, /dd reload, /dd give [player]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("create", "delete", "setcheckpoint", "setfinish", "setitem", "sethomingroom", "finish", "list", "reload", "give"));
        }
        if (args.length == 2 && List.of("delete", "setcheckpoint", "setfinish", "setitem").contains(args[0].toLowerCase())) {
            return partial(args[1], new ArrayList<>(maps.keySet()));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setfinish")) {
            return partial(args[2], List.of("pressure", "sign", "both"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setitem")) {
            return partial(args[2], itemMaterials());
        }
        return Collections.emptyList();
    }

    private List<String> itemMaterials() {
        return java.util.Arrays.stream(Material.values())
                .filter(Material::isItem)
                .map(Material::name)
                .toList();
    }

    private List<String> partial(String input, List<String> values) {
        return values.stream().filter(value -> value.startsWith(input.toLowerCase())).toList();
    }

    private static final class MapMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MapInfo {
        private final String id;
        private final String displayName;
        private final Material itemMaterial;
        private final Location checkpoint;
        private final Location finishLocation;
        private final FinishType finishType;

        private MapInfo(String id, String displayName, Material itemMaterial, Location checkpoint,
                Location finishLocation, FinishType finishType) {
            this.id = id;
            this.displayName = displayName;
            this.itemMaterial = itemMaterial;
            this.checkpoint = checkpoint;
            this.finishLocation = finishLocation;
            this.finishType = finishType;
        }
    }

    private enum FinishType {
        PRESSURE, SIGN, BOTH;

        private static FinishType from(String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException exception) {
                return BOTH;
            }
        }

        private static boolean isValid(String value) {
            try {
                valueOf(value);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private boolean allowsPressure() {
            return this == PRESSURE || this == BOTH;
        }

        private boolean allowsSign() {
            return this == SIGN || this == BOTH;
        }
    }

    private static final class PlayerState {
        private String activeMap;
        private String lastMap;

        private PlayerState(String activeMap, String lastMap) {
            this.activeMap = activeMap;
            this.lastMap = lastMap;
        }
    }
}
