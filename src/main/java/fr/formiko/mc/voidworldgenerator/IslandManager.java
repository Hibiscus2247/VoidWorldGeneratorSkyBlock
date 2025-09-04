package fr.formiko.mc.voidworldgenerator;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

public class IslandManager {
    private final VoidWorldGeneratorPlugin plugin;
    private final Map<UUID, Location> playerIslands;
    private final Set<String> usedPositions; // Track used positions to avoid overlaps
    private final Random random;
    private static final int MAX_RANGE = 100000; // Maximum distance from 0,0
    private static final int MIN_DISTANCE = 200; // Minimum distance between islands

    public IslandManager(VoidWorldGeneratorPlugin plugin) {
        this.plugin = plugin;
        this.playerIslands = new HashMap<>();
        this.usedPositions = new HashSet<>();
        this.random = new Random();
        loadPlayerIslands();
    }

    public void generateIslandForPlayer(Player player) {
        World world = player.getWorld();

        // Check if player already has an island
        if (playerIslands.containsKey(player.getUniqueId())) {
            teleportToIsland(player);
            return;
        }

        // Calculate random island position
        Location islandLocation = calculateRandomIslandLocation(world);

        // Generate island asynchronously to avoid blocking the main thread
        new BukkitRunnable() {
            @Override
            public void run() {
                generateIsland(islandLocation);

                // Teleport player on main thread
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Store island location
                        playerIslands.put(player.getUniqueId(), islandLocation);
                        savePlayerIslands();

                        // Set player's bed spawn location (bed is at spawnLocation.subtract(0, 1, 0))
                        player.sendMessage("§aWelcome! Your personal island has been generated!");
                        Location bedSpawn = player.getBedSpawnLocation();
                        if (bedSpawn != null) {
                            // Player has a valid bed spawn, let Minecraft use it
                            player.sendMessage("Spawning at your bed spawn.");
                        } else {
                            // No bed spawn, use island spawn
                            Location spawnLocation = islandLocation.clone().add(1, 7, 4); // Near the bed
                            player.teleport(spawnLocation);
                            player.setRespawnLocation(spawnLocation);
                        }
                    }
                }.runTask(plugin);
            }
        }.runTask(plugin);
    }

    private Location calculateRandomIslandLocation(World world) {
        Location location;
        String positionKey;
        int attempts = 0;
        int maxAttempts = 100;

        do {
            // Generate random coordinates within the range
            int x = random.nextInt(MAX_RANGE * 2) - MAX_RANGE; // -100000 to +100000
            int z = random.nextInt(MAX_RANGE * 2) - MAX_RANGE; // -100000 to +100000

            // Round to nearest multiple of MIN_DISTANCE to create some spacing
            x = (x / MIN_DISTANCE) * MIN_DISTANCE;
            z = (z / MIN_DISTANCE) * MIN_DISTANCE;

            location = new Location(world, x, 64, z);
            positionKey = x + "," + z;
            attempts++;

        } while (usedPositions.contains(positionKey) && attempts < maxAttempts);

        if (attempts >= maxAttempts) {
            plugin.getLogger().warning("§c[DEBUG] Could not find unique position after " + maxAttempts + " attempts, using last generated position");
        }

        // Mark this position as used
        usedPositions.add(positionKey);

        plugin.getLogger().info("§e[DEBUG] Generated random island location: " + location.getX() + ", " + location.getZ());
        return location;
    }

    private void generateIsland(Location center) {
        World world = center.getWorld();
        if (world == null) {
            plugin.getLogger().warning("§c[DEBUG] World is null, cannot generate island!");
            return;
        }

        plugin.getLogger().info("§e[DEBUG] Generating island at world: " + world.getName());

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        plugin.getLogger().info("§e[DEBUG] Island center coordinates: " + centerX + ", " + centerY + ", " + centerZ);

        int blocksPlaced = 0;

        // Create 6x6x6 cube of dirt
        for (int x = centerX; x < centerX + 6; x++) {
            for (int z = centerZ; z < centerZ + 6; z++) {
                for (int y = centerY; y < centerY + 6; y++) {
                    Block block = world.getBlockAt(x, y, z);

                    // Top layer: grass, everything else: dirt
                    if (y == centerY + 5) {
                        block.setType(Material.GRASS_BLOCK);
                    } else {
                        block.setType(Material.DIRT);
                    }
                    blocksPlaced++;
                }
            }
        }

        plugin.getLogger().info("§e[DEBUG] Placed " + blocksPlaced + " blocks for island");

        // Generate oak tree at one corner (not center) to avoid spawn conflicts
        Location treeLocation = new Location(world, centerX + 4, centerY + 6, centerZ + 4);
        plugin.getLogger().info("§e[DEBUG] Generating tree at: " + treeLocation.getX() + ", " + treeLocation.getY() + ", " + treeLocation.getZ());

        boolean treeGenerated = world.generateTree(treeLocation, TreeType.TREE);
        plugin.getLogger().info("§e[DEBUG] Tree generation " + (treeGenerated ? "successful" : "failed"));

        // Generate chest with starter items at opposite corner from tree
        Location chestLocation = new Location(world, centerX + 1, centerY + 6, centerZ + 1);
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

        if (chestBlock.getState() instanceof Chest) {
            Chest chest = (Chest) chestBlock.getState();
            Inventory chestInventory = chest.getInventory();

            // Add starter items
            chestInventory.addItem(new ItemStack(Material.LAVA_BUCKET, 1));
            chestInventory.addItem(new ItemStack(Material.ICE, 1));

            chest.update();
            plugin.getLogger().info("§e[DEBUG] Generated starter chest with lava bucket and ice block");
        } else {
            plugin.getLogger().warning("§c[DEBUG] Failed to create chest - block state is not a chest");
        }
    }

    public void teleportToIsland(Player player) {
        Location islandLocation = playerIslands.get(player.getUniqueId());
        if (islandLocation != null) {
            Location bedSpawn = player.getBedSpawnLocation();
            if (bedSpawn != null && bedSpawn.getWorld().equals(islandLocation.getWorld())) {
                plugin.getLogger().info("§e[DEBUG] Teleporting " + player.getName() + " to bed spawn: " + bedSpawn);
                player.teleport(bedSpawn);
            } else {
                Location spawnLocation = islandLocation.clone().add(1, 7, 1);
                plugin.getLogger().info("§e[DEBUG] Teleporting " + player.getName() + " to island fallback: " + spawnLocation);
                player.teleport(spawnLocation);
                player.setRespawnLocation(spawnLocation);
            }
        }
        plugin.getLogger().info("§e[DEBUG] teleportToIsland called for " + player.getName());

        Location bedSpawn = player.getBedSpawnLocation();
        if (bedSpawn != null) {
            plugin.getLogger().info("§e[DEBUG] Bed spawn detected: " + bedSpawn);
        } else {
            plugin.getLogger().info("§e[DEBUG] No bed spawn detected.");
        }

        Location fallback = player.getRespawnLocation();
        plugin.getLogger().info("§e[DEBUG] Fallback respawn location: " + fallback);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType().toString().contains("BED")) {
                Bukkit.getLogger().info("§e[DEBUG] " + event.getPlayer().getName() + " interacted with bed at " + block.getLocation());
            }
        }
    }


    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location respawnLocation = event.getRespawnLocation();

        Bukkit.getLogger().info("§e[DEBUG] " + player.getName() + " is respawning at: " + respawnLocation);

        Location bedSpawn = player.getBedSpawnLocation();
        if (bedSpawn != null) {
            Bukkit.getLogger().info("§e[DEBUG] Bed spawn exists at: " + bedSpawn);
        } else {
            Bukkit.getLogger().info("§e[DEBUG] No bed spawn found for " + player.getName());
        }

        Location fallbackSpawn = player.getRespawnLocation();
        if (fallbackSpawn != null) {
            Bukkit.getLogger().info("§e[DEBUG] Fallback respawn location set to: " + fallbackSpawn);
        } else {
            Bukkit.getLogger().info("§e[DEBUG] No fallback respawn location set.");
        }
    }



    public boolean hasIsland(Player player) {
        return playerIslands.containsKey(player.getUniqueId());
    }

    public Location getIslandSpawnLocation(Player player) {
        Location islandLocation = playerIslands.get(player.getUniqueId());
        if (islandLocation != null) {
            return islandLocation.clone().add(1, 7, 1); // Same spawn location as teleport
        }
        return null;
    }

    private void loadPlayerIslands() {
        FileConfiguration config = plugin.getConfig();
        if (config.contains("playerIslands")) {
            for (String uuidString : config.getConfigurationSection("playerIslands").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String worldName = config.getString("playerIslands." + uuidString + ".world");
                    double x = config.getDouble("playerIslands." + uuidString + ".x");
                    double y = config.getDouble("playerIslands." + uuidString + ".y");
                    double z = config.getDouble("playerIslands." + uuidString + ".z");

                    World world = plugin.getServer().getWorld(worldName);
                    if (world != null) {
                        Location location = new Location(world, x, y, z);
                        playerIslands.put(uuid, location);

                        // Mark this position as used
                        String positionKey = (int)x + "," + (int)z;
                        usedPositions.add(positionKey);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load island for player: " + uuidString);
                }
            }
        }

        plugin.getLogger().info("§e[DEBUG] Loaded " + playerIslands.size() + " player islands");
        plugin.getLogger().info("§e[DEBUG] Loaded " + usedPositions.size() + " used positions");
    }

    private void savePlayerIslands() {
        FileConfiguration config = plugin.getConfig();

        // Clear existing data
        config.set("playerIslands", null);

        // Save player islands
        for (Map.Entry<UUID, Location> entry : playerIslands.entrySet()) {
            String uuidString = entry.getKey().toString();
            Location location = entry.getValue();

            config.set("playerIslands." + uuidString + ".world", location.getWorld().getName());
            config.set("playerIslands." + uuidString + ".x", location.getX());
            config.set("playerIslands." + uuidString + ".y", location.getY());
            config.set("playerIslands." + uuidString + ".z", location.getZ());
        }

        plugin.saveConfig();
    }
}