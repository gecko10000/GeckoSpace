package gecko10000.geckospace.dimensions.resource

import com.nexomc.nexo.api.NexoItems
import gecko10000.geckolib.extensions.parseMM
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.koin.core.component.inject
import java.util.*
import java.util.concurrent.CompletableFuture

class ShifterManager : MyKoinComponent, Listener {

    private val plugin: GeckoSpace by inject()

    private fun getOppositeWorld(source: World): World? {
        val srcName = source.name
        val entry = plugin.config.resourceWorldPairs.entries.firstOrNull { it.key == srcName || it.value == srcName }
            ?: return null
        val otherName = if (entry.key == srcName) entry.value else entry.key
        return Bukkit.getWorld(otherName)
    }

    private fun iterateDown(block: Block): Location? {
        var curr = block.world.getHighestBlockAt(block.location)
        var prev = curr.getRelative(BlockFace.UP)
        if (block.world.environment == World.Environment.NETHER) {
            prev = curr
            curr = curr.getRelative(BlockFace.DOWN)
        }
        while (curr.y >= block.world.minHeight) {
            if (curr.type !in plugin.config.unsafeBlockTypes
                && prev.type.isAir
                && prev.getRelative(BlockFace.UP).type.isAir
            ) {
                return curr.location.add(0.5, 1.0, 0.5)
            }
            prev = curr
            curr = curr.getRelative(BlockFace.DOWN)
        }
        return null
    }

    private fun getSafeSpot(block: Block): CompletableFuture<Location?> {
        return block.world.getChunkAtAsync(block)
            .thenApply { iterateDown(block) }
    }

    private val alreadyTeleporting = mutableSetOf<UUID>()

    @EventHandler
    private fun PlayerInteractEvent.onShifterUse() {
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return
        val id = NexoItems.idFromItem(item)
        if (id != plugin.config.dimensionShifterItemId) return
        if (player.uniqueId in alreadyTeleporting) return
        val sourceWorld = player.world
        val destWorld = getOppositeWorld(sourceWorld) ?: return run {
            player.sendActionBar(parseMM("<red>You can't use this here..."))
        }
        val destColumnBlock = destWorld.getBlockAt(player.x.toInt(), 0, player.z.toInt())
        alreadyTeleporting += player.uniqueId
        getSafeSpot(destColumnBlock).thenApply { dest ->
            dest ?: return@thenApply run {
                alreadyTeleporting -= player.uniqueId
                player.sendActionBar(parseMM("<red>It's not safe to shift here..."))
            }
            player.teleportAsync(dest).thenRun {
                alreadyTeleporting -= player.uniqueId
                player.sendActionBar(parseMM("<green>Shift successful."))
            }
        }
    }

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

}
