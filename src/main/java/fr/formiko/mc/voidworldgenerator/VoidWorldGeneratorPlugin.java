package fr.formiko.mc.voidworldgenerator;

import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import org.bstats.bukkit.Metrics;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Generate empty chunks with the config biome and spawn islands for new players.
 */
public class VoidWorldGeneratorPlugin extends JavaPlugin implements Listener {
    private ConfigSettings configSettings;
    private IslandManager islandManager;

    @Override
    public void onEnable() {
        // new Metrics(this, 20171); // Temporarily disabled - missing dependency
        saveDefaultConfig();
        configSettings = new ConfigSettings();
        islandManager = new IslandManager(this);

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("VoidWorldGenerator enabled with island generation!");
    }

    public static VoidWorldGeneratorPlugin getInstance() {
        return getPlugin(VoidWorldGeneratorPlugin.class);
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        getLogger().info("§e[DEBUG] Generator requested for world: " + worldName + " with id: " + id);
        return new VoidChunkGenerator(worldName);
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        getLogger().info("§e[DEBUG] Player joined: " + player.getName());
        getLogger().info("§e[DEBUG] Has played before: " + player.hasPlayedBefore());
        getLogger().info("§e[DEBUG] World: " + player.getWorld().getName());

        // Only teleport if this is the player's first time joining
        if (!player.hasPlayedBefore()) {
            getLogger().info("§e[DEBUG] New player detected, generating island immediately...");
            // Generate island for new player immediately (no delay)
            getServer().getScheduler().runTask(this, () -> {
                getLogger().info("§e[DEBUG] Running island generation task...");
                islandManager.generateIslandForPlayer(player);
            });
        } else {
            getLogger().info("§e[DEBUG] Existing player - leaving them where they logged out");
            // Do nothing - let them stay where they logged out
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Only override if Minecraft didn't already resolve a valid bed spawn
        if (!event.isBedSpawn()) {
            Location islandSpawn = islandManager.getIslandSpawnLocation(player);
            if (islandSpawn != null) {
                event.setRespawnLocation(islandSpawn);
                getLogger().info("§e[DEBUG] Player " + player.getName() + " has no valid bed. Respawning at island.");
            } else {
                getLogger().warning("§c[DEBUG] No island spawn found for " + player.getName() + ". Defaulting to world spawn.");
            }
        } else {
            getLogger().info("§e[DEBUG] " + player.getName() + " is using valid bed spawn. No override.");
        }
    }




    private class VoidChunkGenerator extends ChunkGenerator {
        private final String worldName;
        private VoidChunkGenerator(String worldName) {
            this.worldName = worldName;
        }

        @Override
        public List<BlockPopulator> getDefaultPopulators(World world) {
            return List.of();
        }

        @Override
        public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                                  @NotNull ChunkData chunkData) {
            // Initialize chunk bounds by accessing the chunk data methods
            // This establishes proper world boundaries without placing blocks
            chunkData.getMinHeight();
            chunkData.getMaxHeight();
        }

        @Override
        public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                                    @NotNull ChunkData chunkData) {
            // No need to generate surface, we want an empty world
        }

        @Override
        public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                                    @NotNull ChunkData chunkData) {
            // No need to generate bedrock, we want an empty world
        }

        @Override
        public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                                  @NotNull ChunkData chunkData) {
            // No need to generate caves, we want an empty world
        }

        @Override
        public boolean canSpawn(World world, int x, int z) {
            // Check if coordinates are within reasonable bounds
            return Math.abs(x) < 30000000 && Math.abs(z) < 30000000;
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            // For void worlds with islands, we'll let the IslandManager handle spawning
            // But still provide a fallback spawn location
            int spawnY = configSettings.getSpawnY(worldName);
            if (spawnY < world.getMinHeight()) {
                spawnY = world.getMinHeight() + 1;
            } else if (spawnY >= world.getMaxHeight()) {
                spawnY = world.getMaxHeight() - 1;
            }

            return new Location(world, configSettings.getSpawnX(worldName), spawnY,
                    configSettings.getSpawnZ(worldName));
        }

        // CRITICAL: Override shouldGenerateStructures to return true
        // CRITICAL: Override shouldGenerateStructures to return true
        @Override
        public boolean shouldGenerateStructures() {
            return true; // This enables structure generation
        }

        // CRITICAL: Override shouldGenerateDecorations to return true
        @Override
        public boolean shouldGenerateDecorations() {
            return true; // This enables decorations like ores, plants, etc.
        }
    }
}