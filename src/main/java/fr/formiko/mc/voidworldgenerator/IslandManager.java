package fr.formiko.mc.voidworldgenerator;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class IslandManager {
    private final VoidWorldGeneratorPlugin plugin;
    private final Map<UUID, Location> playerIslands;
    private final Set<String> usedPositions;
    private final Random random;
    private static final int MAX_RANGE = 100000;
    private static final int MIN_DISTANCE = 200;

    public IslandManager(VoidWorldGeneratorPlugin plugin) {
        this.plugin = plugin;
        this.playerIslands = new HashMap<>();
        this.usedPositions = new HashSet<>();
        this.random = new Random();
        loadPlayerIslands();
    }

    public void generateIslandForPlayer(Player player) {
        World world = player.getWorld();

        if (playerIslands.containsKey(player.getUniqueId())) {
            teleportToIsland(player);
            return;
        }

        Location islandLocation = calculateRandomIslandLocation(world);

        new BukkitRunnable() {
            @Override
            public void run() {
                generateIsland(islandLocation);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        playerIslands.put(player.getUniqueId(), islandLocation);
                        savePlayerIslands();

                        Location spawnLocation = islandLocation.clone().add(1, 7, 4);
                        Location bedLocation = islandLocation.clone().add(1, 6, 4);

                        player.teleport(spawnLocation);
                        player.setBedSpawnLocation(bedLocation, true);

                        plugin.getLogger().info("§e[DEBUG] Teleported " + player.getName() + " to island spawn: " + spawnLocation);
                        player.sendMessage("§aWelcome! Your personal island has been generated!");
                    }
                }.runTask(plugin);
            }
        }.runTask(plugin);
    }

    public void teleportToIsland(Player player) {
        Location islandLocation = playerIslands.get(player.getUniqueId());
        if (islandLocation != null) {
            Location spawnLocation = islandLocation.clone().add(1, 7, 1);
            Location bedSpawn = player.getBedSpawnLocation();

            boolean bedValid = bedSpawn != null && bedSpawn.getBlock().getType().toString().contains("BED");

            if (bedValid) {
                plugin.getLogger().info("§e[DEBUG] " + player.getName() + " has valid bed spawn: " + bedSpawn);
                player.teleport(bedSpawn);
            } else {
                plugin.getLogger().info("§e[DEBUG] " + player.getName() + " has no valid bed. Teleporting to island: " + spawnLocation);
                player.teleport(spawnLocation);
                player.setRespawnLocation(spawnLocation);
            }
        }
    }

    private Location calculateRandomIslandLocation(World world) {
        Location location;
        String positionKey;
        int attempts = 0;

        do {
            int x = (random.nextInt(MAX_RANGE * 2) - MAX_RANGE) / MIN_DISTANCE * MIN_DISTANCE;
            int z = (random.nextInt(MAX_RANGE * 2) - MAX_RANGE) / MIN_DISTANCE * MIN_DISTANCE;

            location = new Location(world, x, 64, z);
            positionKey = x + "," + z;
            attempts++;
        } while (usedPositions.contains(positionKey) && attempts < 100);

        usedPositions.add(positionKey);
        plugin.getLogger().info("§e[DEBUG] Generated island at: " + location);
        return location;
    }

    private void generateIsland(Location center) {
        World world = center.getWorld();
        if (world == null) {
            plugin.getLogger().warning("§c[DEBUG] World is null, cannot generate island!");
            return;
        }

        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int blocksPlaced = 0;

        for (int x = cx; x < cx + 6; x++) {
            for (int z = cz; z < cz + 6; z++) {
                for (int y = cy; y < cy + 6; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(y == cy + 5 ? Material.GRASS_BLOCK : Material.DIRT);
                    blocksPlaced++;
                }
            }
        }

        plugin.getLogger().info("§e[DEBUG] Placed " + blocksPlaced + " blocks for island");

        Location treeLocation = new Location(world, cx + 4, cy + 6, cz + 4);
        boolean treeSuccess = world.generateTree(treeLocation, TreeType.TREE);
        plugin.getLogger().info("§e[DEBUG] Tree generation " + (treeSuccess ? "successful" : "failed"));

        Location chestLocation = new Location(world, cx + 1, cy + 6, cz + 1);
        generateStarterChest(chestLocation);
    }

    private void generateStarterChest(Location location) {
        World world = location.getWorld();
        if (world == null) {
            plugin.getLogger().warning("§c[DEBUG] World is null, cannot generate chest!");
            return;
        }

        Block chestBlock = world.getBlockAt(location);
        chestBlock.setType(Material.CHEST);

        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getInventory();
            inv.addItem(new ItemStack(Material.LAVA_BUCKET));
            inv.addItem(new ItemStack(Material.ICE));
            chest.update();
            plugin.getLogger().info("§e[DEBUG] Starter chest created.");
        } else {
            plugin.getLogger().warning("§c[DEBUG] Failed to create chest.");
        }
    }

    public boolean hasIsland(Player player) {
        return playerIslands.containsKey(player.getUniqueId());
    }

    public Location getIslandSpawnLocation(Player player) {
        Location islandLocation = playerIslands.get(player.getUniqueId());
        return islandLocation != null ? islandLocation.clone().add(1, 7, 1) : null;
    }

    private void loadPlayerIslands() {
        FileConfiguration config = plugin.getConfig();
        if (config.contains("playerIslands")) {
            for (String uuidStr : config.getConfigurationSection("playerIslands").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String worldName = config.getString("playerIslands." + uuidStr + ".world");
                    double x = config.getDouble("playerIslands." + uuidStr + ".x");
                    double y = config.getDouble("playerIslands." + uuidStr + ".y");
                    double z = config.getDouble("playerIslands." + uuidStr + ".z");

                    World world = plugin.getServer().getWorld(worldName);
                    if (world != null) {
                        Location loc = new Location(world, x, y, z);
                        playerIslands.put(uuid, loc);
                        usedPositions.add((int)x + "," + (int)z);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load island for player: " + uuidStr);
                }
            }
        }

        plugin.getLogger().info("§e[DEBUG] Loaded " + playerIslands.size() + " islands");
    }

    private void savePlayerIslands() {
        FileConfiguration config = plugin.getConfig();
        config.set("playerIslands", null);

        for (Map.Entry<UUID, Location> entry : playerIslands.entrySet()) {
            UUID uuid = entry.getKey();
            Location loc = entry.getValue();
            config.set("playerIslands." + uuid + ".world", loc.getWorld().getName());
            config.set("playerIslands." + uuid + ".x", loc.getX());
            config.set("playerIslands." + uuid + ".y", loc.getY());
            config.set("playerIslands." + uuid + ".z", loc.getZ());
        }

        plugin.saveConfig();
    }

    public VoidWorldGeneratorPlugin getPlugin() {
        return plugin;
    }
}
