package gecko10000.geckospace.dimensions.mantle

import com.nexomc.nexo.api.NexoBlocks
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.PortalCreateEvent
import org.koin.core.component.inject
import redempt.redlib.misc.EventListener
import redempt.redlib.misc.Task
import kotlin.math.min

// Mantle is just the nether,
// with custom entry/exit mechanics.
class MantleManager : MyKoinComponent {

    private val plugin: GeckoSpace by inject()

    private val litColumns = mutableSetOf<Pair<Int, Int>>()

    private fun getLowestPortalY(blocks: List<Block>): Int {
        var min = blocks[0].y
        for (i in 1..<blocks.size) {
            min = min(min, blocks[i].y)
        }
        return min
    }

    private fun crackBedrock(block: Block) {
        NexoBlocks.place(plugin.config.crackedBedrockId, block.location)
        block.world.strikeLightningEffect(block.location.add(0.5, 0.0, 0.5))
    }

    init {
        EventListener(PlayerInteractEvent::class.java, EventPriority.MONITOR) { e ->
            if (e.useItemInHand() == Event.Result.DENY) return@EventListener
            val block = e.clickedBlock
            if (e.action != Action.RIGHT_CLICK_BLOCK || block == null) return@EventListener
            val item = e.item ?: return@EventListener
            if (item.type != Material.FLINT_AND_STEEL) return@EventListener
            val fireBlock = block.getRelative(e.blockFace)
            val coords = fireBlock.x to fireBlock.z
            litColumns += coords
            Task.syncDelayed { -> litColumns.remove(coords) }
        }
        EventListener(PortalCreateEvent::class.java) { e ->
            e.isCancelled = true
            val blocks = e.blocks.map { it.block }
            if (e.reason != PortalCreateEvent.CreateReason.FIRE) {
                println("Unknown portal created: ${e.reason}")
                println(blocks)
                return@EventListener
            }
            val lowestY = getLowestPortalY(blocks)
            val lowestBlocks = blocks.filter { it.y == lowestY }
            val blockInLitColumn = lowestBlocks.firstOrNull { (it.x to it.z) in litColumns } ?: lowestBlocks.first()
            val blockBelow = blockInLitColumn.getRelative(BlockFace.DOWN)
            if (blockBelow.type != Material.BEDROCK) return@EventListener
            // Peter, what are you doing?
            crackBedrock(blockBelow)
            blocks.forEach { block ->
                if (block.type == Material.OBSIDIAN) {
                    block.type = Material.AIR
                }
            }
        }
    }

}
