package fr.formiko.mc.voidworldgenerator;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
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
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            World world = location.getWorld();
            if (world == null) {
                plugin.getLogger().warning("§c[ERROR] World is null, can't generate chest at " + location);
                return;
            }

            // Ensure chunk is loaded and stays loaded
            if (!location.getChunk().isLoaded()) {
                location.getChunk().load(true);
            }
            location.getChunk().setForceLoaded(true);

            // Set the chest block
            Block block = location.getBlock();
            block.setType(Material.CHEST, true);

            plugin.getLogger().info("§e[DEBUG] Placed chest block at " + location);

            // Wait for the block to fully initialize
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                populateChestCorrectly(location, 0);
            }, 20L); // 1 second delay
        });
    }

    private void populateChestCorrectly(Location location, int attempt) {
        if (attempt > 0 && attempt % 20 == 0) {
            plugin.getLogger().info("§a[PERSISTENT] Still trying to populate chest... attempt " + attempt);
        }

        Block block = location.getBlock();

        // Verify chunk is still loaded
        if (!location.getChunk().isLoaded()) {
            plugin.getLogger().info("§e[DEBUG] Chunk unloaded, reloading...");
            location.getChunk().load(true);
            location.getChunk().setForceLoaded(true);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                populateChestCorrectly(location, attempt + 1);
            }, 40L);
            return;
        }

        // Verify block is still a chest
        if (block.getType() != Material.CHEST) {
            plugin.getLogger().info("§e[DEBUG] Block changed to: " + block.getType() + ", recreating chest...");
            block.setType(Material.CHEST, true);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                populateChestCorrectly(location, attempt + 1);
            }, 40L);
            return;
        }

        try {
            // METHOD 1: Direct inventory access without BlockState update
            plugin.getLogger().info("§e[DEBUG] Attempt " + (attempt + 1) + " - Using direct inventory access");

            // Get the block as a chest and access its inventory directly
            if (block.getState() instanceof Chest chest) {
                Inventory inventory = chest.getBlockInventory();

                // Clear and populate the inventory
                inventory.clear();

                ItemStack[] items = {
                        new ItemStack(Material.LAVA_BUCKET, 1),
                        new ItemStack(Material.WATER_BUCKET, 1)
                };

                // Set items directly in the inventory
                for (int i = 0; i < items.length; i++) {
                    inventory.setItem(i, items[i]);
                }

                plugin.getLogger().info("§e[DEBUG] Items set directly in inventory: " + Arrays.toString(inventory.getContents()));

                // DO NOT call chest.update() - this is what was clearing the inventory!
                // The inventory changes are automatically saved when we modify the live inventory

                // Verify immediately
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    verifyChestPopulation(location, attempt);
                }, 10L);

            } else {
                plugin.getLogger().info("§e[DEBUG] Block state is not a chest, retrying...");
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    populateChestCorrectly(location, attempt + 1);
                }, 40L);
            }

        } catch (Exception e) {
            plugin.getLogger().info("§c[DEBUG] Exception during chest population: " + e.getMessage());
            e.printStackTrace();

            // Fallback: Try the alternative approach
            if (attempt < 50) { // Reasonable retry limit
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    tryAlternativeChestPopulation(location, attempt + 1);
                }, 60L);
            } else {
                plugin.getLogger().warning("§c[ERROR] Failed to populate chest after 50 attempts, giving up.");
            }
        }
    }

    private void tryAlternativeChestPopulation(Location location, int attempt) {
        plugin.getLogger().info("§e[DEBUG] Trying alternative approach - using setContents()");

        try {
            Block block = location.getBlock();
            if (block.getType() != Material.CHEST) {
                plugin.getLogger().info("§c[DEBUG] Block changed during alternative method");
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    populateChestCorrectly(location, attempt + 1);
                }, 60L);
                return;
            }

            if (block.getState() instanceof Chest chest) {
                Inventory inventory = chest.getBlockInventory();

                // Create the complete contents array
                ItemStack[] contents = new ItemStack[27]; // Standard chest size
                contents[0] = new ItemStack(Material.LAVA_BUCKET, 1);
                contents[1] = new ItemStack(Material.ICE, 1);
                contents[2] = new ItemStack(Material.BREAD, 16);
                contents[3] = new ItemStack(Material.OAK_SAPLING, 4);
                contents[4] = new ItemStack(Material.BONE_MEAL, 8);
                contents[5] = new ItemStack(Material.WATER_BUCKET, 1);

                // Use setContents() instead of individual setItem() calls
                inventory.setContents(contents);

                plugin.getLogger().info("§e[DEBUG] Used setContents() method");

                // Verify the results
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    verifyChestPopulation(location, attempt);
                }, 10L);

            } else {
                plugin.getLogger().info("§c[DEBUG] Alternative method failed to get chest");
                if (attempt < 50) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        populateChestCorrectly(location, attempt + 1);
                    }, 60L);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().info("§c[DEBUG] Exception in alternative method: " + e.getMessage());
            if (attempt < 50) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    populateChestCorrectly(location, attempt + 1);
                }, 60L);
            }
        }
    }

    private void verifyChestPopulation(Location location, int attempt) {
        try {
            Block block = location.getBlock();

            if (block.getType() != Material.CHEST) {
                plugin.getLogger().info("§c[DEBUG] Block no longer a chest during verification");
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    populateChestCorrectly(location, attempt + 1);
                }, 60L);
                return;
            }

            BlockState state = block.getState();
            if (!(state instanceof Chest chest)) {
                plugin.getLogger().info("§c[DEBUG] BlockState not a chest during verification");
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    populateChestCorrectly(location, attempt + 1);
                }, 60L);
                return;
            }

            // Use getBlockInventory() for verification too
            Inventory inventory = chest.getBlockInventory();
            ItemStack[] contents = inventory.getContents();

            int itemCount = 0;
            for (ItemStack item : contents) {
                if (item != null && item.getType() != Material.AIR) {
                    itemCount++;
                }
            }

            if (itemCount >= 6) {
                plugin.getLogger().info("§a[SUCCESS] Chest successfully populated with " + itemCount + " items after " + (attempt + 1) + " attempts!");

                // Log contents for verification
                for (int i = 0; i < Math.min(contents.length, 10); i++) {
                    if (contents[i] != null && contents[i].getType() != Material.AIR) {
                        plugin.getLogger().info("§a[CONTENTS] Slot " + i + ": " + contents[i].getType() + " x" + contents[i].getAmount());
                    }
                }

                // Success! Stop retrying
                return;

            } else {
                plugin.getLogger().info("§e[DEBUG] Verification failed - only " + itemCount + " items found (attempt " + (attempt + 1) + ") - retrying...");

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    populateChestCorrectly(location, attempt + 1);
                }, 80L); // 4 second delay
            }

        } catch (Exception e) {
            plugin.getLogger().info("§c[DEBUG] Exception during verification: " + e.getMessage());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                populateChestCorrectly(location, attempt + 1);
            }, 80L);
        }
    }

    private void generateStarterChestWithRetry(Location location, int attempts) {
        plugin.getLogger().info("§e[DEBUG] Starting chest generation using 1.21.8 API at " + location);
        generateStarterChest(location);
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
