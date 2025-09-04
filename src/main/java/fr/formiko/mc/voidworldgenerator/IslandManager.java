package fr.formiko.mc.voidworldgenerator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IslandManager {
    private final VoidWorldGeneratorPlugin plugin;
    private final Map<UUID, Location> playerIslands;
    private int nextIslandX = 0;
    private int nextIslandZ = 0;
    private static final int ISLAND_SPACING = 100; // Distance between islands

    public IslandManager(VoidWorldGeneratorPlugin plugin) {
        this.plugin = plugin;
        this.playerIslands = new HashMap<>();
        loadPlayerIslands();
    }

    public void generateIslandForPlayer(Player player) {
        World world = player.getWorld();

        // Check if player already has an island
        if (playerIslands.containsKey(player.getUniqueId())) {
            teleportToIsland(player);
            return;
        }

        // Calculate island position
        Location islandLocation = calculateNextIslandLocation(world);

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

                        // Teleport player to their new island
                        Location spawnLocation = islandLocation.clone().add(3, 7, 3); // Top center of island
                        player.teleport(spawnLocation);
                        player.setRespawnLocation(spawnLocation);

                        player.sendMessage("§aWelcome! Your personal island has been generated!");
                    }
                }.runTask(plugin);
            }
        }.runTask(plugin);
    }

    private Location calculateNextIslandLocation(World world) {
        // Simple grid pattern for island placement
        Location location = new Location(world, nextIslandX, 64, nextIslandZ);

        // Move to next position
        nextIslandX += ISLAND_SPACING;
        if (nextIslandX > 1000) { // Reset X and move Z forward after 10 islands in a row
            nextIslandX = 0;
            nextIslandZ += ISLAND_SPACING;
        }

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

        // Create 6x6x6 cube of dirt (actually 6x6x6 means 6 blocks in each direction)
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

        // Generate oak tree on top of the island
        // Tree should be placed at the center-ish of the top surface
        Location treeLocation = new Location(world, centerX + 3, centerY + 6, centerZ + 3);
        plugin.getLogger().info("§e[DEBUG] Generating tree at: " + treeLocation.getX() + ", " + treeLocation.getY() + ", " + treeLocation.getZ());

        // Use Bukkit's built-in tree generation (on main thread)
        boolean treeGenerated = world.generateTree(treeLocation, TreeType.TREE); // TREE is oak tree
        plugin.getLogger().info("§e[DEBUG] Tree generation " + (treeGenerated ? "successful" : "failed"));
    }

    public void teleportToIsland(Player player) {
        Location islandLocation = playerIslands.get(player.getUniqueId());
        if (islandLocation != null) {
            Location spawnLocation = islandLocation.clone().add(3, 7, 3);
            player.teleport(spawnLocation);
        }
    }

    public boolean hasIsland(Player player) {
        return playerIslands.containsKey(player.getUniqueId());
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
                        playerIslands.put(uuid, new Location(world, x, y, z));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load island for player: " + uuidString);
                }
            }
        }

        // Load next island position
        nextIslandX = config.getInt("nextIslandX", 0);
        nextIslandZ = config.getInt("nextIslandZ", 0);
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

        // Save next island position
        config.set("nextIslandX", nextIslandX);
        config.set("nextIslandZ", nextIslandZ);

        plugin.saveConfig();
    }
}