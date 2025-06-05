package gecko10000.geckospace.dimensions.mantle

import gecko10000.geckolib.playerplaced.PlayerPlacedBlockTracker
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import io.papermc.paper.event.entity.EntityInsideBlockEvent
import io.papermc.paper.math.BlockPosition
import io.papermc.paper.math.Position
import org.bukkit.Bukkit
import org.bukkit.HeightMap
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.PortalCreateEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import org.koin.core.component.inject
import redempt.redlib.misc.Task
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.collections.ArrayDeque
import kotlin.collections.Set
import kotlin.collections.any
import kotlin.collections.distinct
import kotlin.collections.first
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.mutableSetOf
import kotlin.collections.plusAssign
import kotlin.collections.setOf
import kotlin.collections.toTypedArray
import kotlin.math.ceil

@Suppress("UnstableApiUsage")
class TeleportationManager : MyKoinComponent, Listener {

    private val plugin: GeckoSpace by inject()

    // Prevent multiple teleportations from touching multiple blocks
    private val alreadyTeleporting = ConcurrentSkipListSet<UUID>()

    @EventHandler
    private fun EntityInsideBlockEvent.onEndPortalTouch() {
        val block = this.block
        if (block.type != Material.END_PORTAL) return
        if (justTeleportedBack.contains(entity.uniqueId)) return
        if (!alreadyTeleporting.add(entity.uniqueId)) return
        isCancelled = true
        val inOverworld = plugin.config.netherWorldPairs.any { it.key == block.world.name }
        if (inOverworld) {
            portalToNether(entity, block)
        } else {
            portalToOverworld(entity, block)
        }
    }

    private fun findConnectedPortal(
        world: World,
        block: BlockPosition,
    ): Set<BlockPosition> {
        val portal = mutableSetOf<BlockPosition>()
        val visited = mutableSetOf<BlockPosition>()
        val queue = ArrayDeque<BlockPosition>()
        queue += block
        while (queue.isNotEmpty()) {
            val nextPos = queue.removeFirst()
            val added = visited.add(nextPos)
            if (!added) continue
            if (nextPos.toLocation(world).block.type != Material.END_PORTAL) continue
            portal += nextPos
            for (face in setOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
                queue += nextPos.offset(face)
            }
        }
        return portal
    }

    // The 3 blocks below the portal block may be bedrock.
    private fun clearTopBedrock(portalBlock: Block) {
        var block = portalBlock
        for (i in 1..4) {
            block = block.getRelative(BlockFace.DOWN)
            if (block.type == Material.BEDROCK) {
                block.type = Material.NETHERRACK
            }
        }
    }

    // Clears enough room for the entity,
    // but only removes non-player-placed blocks.
    private fun makeRoomFor(entity: Entity, portalBlock: Block) {
        val entityHeight = ceil(entity.height).toInt()
        var block = portalBlock
        for (i in 0..<entityHeight) {
            block = block.getRelative(BlockFace.DOWN)
            if (PlayerPlacedBlockTracker.isPlayerPlaced(block)) continue
            block.type = Material.AIR
        }
    }

    private fun portalToNether(entity: Entity, block: Block) {
        val worldPair = plugin.config.netherWorldPairs.entries.first { it.key == entity.world.name }
        val destWorld = Bukkit.getWorld(worldPair.value) ?: return // NOTTODO: not my problem
        val portalBlockPositions = findConnectedPortal(block.world, Position.block(block.location))
        val bottomBlocks = portalBlockPositions.map { it.toLocation(destWorld).block }
        val chunkKeysToLoad = bottomBlocks.map { it.x / 16 to it.z / 16 }.distinct()
        val chunkLoadFutures = chunkKeysToLoad.map { destWorld.getChunkAtAsync(it.first, it.second) }
        CompletableFuture.allOf(*chunkLoadFutures.toTypedArray())
            .thenCompose {
                val portalBlocks =
                    bottomBlocks.map { destWorld.getHighestBlockAt(it.x, it.z, HeightMap.WORLD_SURFACE) }
                portalBlocks.forEach {
                    it.type = Material.END_PORTAL
                    clearTopBedrock(it)
                    makeRoomFor(entity, it)
                }
                val height = ceil(entity.height).toInt()
                val destLocation = entity.location.clone()
                destLocation.world = destWorld
                destLocation.y = portalBlocks.first().y.toDouble() - height
                entity.fallDistance = 0f // because I'm so nice
                return@thenCompose entity.teleportAsync(destLocation).thenRun {
                    alreadyTeleporting.remove(entity.uniqueId)
                }
            }.handle { _, ex ->
                ex.printStackTrace()
            }
    }

    private val justTeleportedBack = mutableSetOf<UUID>()

    private fun launchUp(entity: Entity, velocity: Vector = entity.velocity) {
        entity.velocity = velocity.setY(1)
        (entity as? LivingEntity)?.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 2 * 20, 0))
    }

    private fun portalToOverworld(entity: Entity, block: Block) {
        val worldPair = plugin.config.netherWorldPairs.entries.first { it.value == entity.world.name }
        val destWorld = Bukkit.getWorld(worldPair.key) ?: return
        val portalBlockPositions = findConnectedPortal(block.world, Position.block(block.location))
        val topBlocks = portalBlockPositions.map { it.toLocation(destWorld).block }
        val chunkKeysToLoad = topBlocks.map { it.x / 16 to it.z / 16 }.distinct()
        val chunkLoadFutures = chunkKeysToLoad.map { destWorld.getChunkAtAsync(it.first, it.second) }
        CompletableFuture.allOf(*chunkLoadFutures.toTypedArray())
            .thenCompose {
                // No clearing
                val destLocation = entity.location.clone()
                destLocation.world = destWorld
                destLocation.y = destWorld.minHeight + 1.0
                val currentVelocity = entity.velocity
                return@thenCompose entity.teleportAsync(
                    destLocation,
                    PlayerTeleportEvent.TeleportCause.PLUGIN,
                ).thenRun {
                    if (entity is Player) {
                        justTeleportedBack += entity.uniqueId
                    }
                    launchUp(entity, velocity = currentVelocity)
                    alreadyTeleporting.remove(entity.uniqueId)
                }
            }.handle { _, ex ->
                ex.printStackTrace()
            }
    }

    private fun moveAfterPortal(isHorizOrDown: () -> Boolean, entity: Entity): Boolean {
        val didJustTeleport = justTeleportedBack.contains(entity.uniqueId)
        if (!didJustTeleport) return false
        if (isHorizOrDown()) {
            // Moving down, prevent and set velocity again
            Task.syncDelayed { ->
                launchUp(entity)
            }
            return true
        } else {
            // Moving up
            justTeleportedBack.remove(entity.uniqueId)
            return false
        }
    }

    // Prevent player from falling until he has moved up.
    @EventHandler(ignoreCancelled = true)
    private fun PlayerMoveEvent.onMoveAfterPortal() {
        isCancelled = moveAfterPortal({ to.y <= from.y }, player)
    }

    @EventHandler(ignoreCancelled = true)
    private fun PortalCreateEvent.onNetherPortalLight() {
        if (this.reason != PortalCreateEvent.CreateReason.FIRE) return
        isCancelled = true
        if (this.entity != null) {
            // TODO: journal entry
        }
    }

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

}
