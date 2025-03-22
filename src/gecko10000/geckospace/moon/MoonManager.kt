package gecko10000.geckospace.moon

import com.nexomc.nexo.api.NexoBlocks
import com.nexomc.nexo.api.NexoItems
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import gecko10000.geckospace.util.ToolTier
import gecko10000.geckospace.util.ToolType
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.koin.core.component.inject
import redempt.redlib.blockdata.BlockDataManager
import redempt.redlib.misc.EventListener

class MoonManager : MyKoinComponent {

    private val plugin: GeckoSpace by inject()
    private val bdm = BlockDataManager.createPDC(plugin, true, true)

    private fun updateChunk(chunk: Chunk) {
        bdm.getLoaded(chunk.world, chunk.x, chunk.z)
    }

    private fun handleMoonstones() {
        EventListener(BlockBreakEvent::class.java) { e ->
            if (e.isCancelled) return@EventListener
            val location = e.block.location
            val nexoId = NexoBlocks.customBlockMechanic(location)?.itemID ?: return@EventListener
            // Prevent breaking empty moonstones
            if (nexoId == plugin.config.moonstoneEmptyId) {
                e.isCancelled = true
                return@EventListener
            }
            // Break full ones by replacing with an empty one
            // and dropping the item if mined with the right block.
            if (nexoId == plugin.config.moonstoneFullId) {
                e.isCancelled = true
                NexoBlocks.place(plugin.config.moonstoneEmptyId, location)

                val usedItem = e.player.inventory.itemInMainHand
                val isRightTier = ToolTier.fromItemStack(usedItem)?.isAtLeast(plugin.config.moonstoneMinTier) == true
                val isRightTool = ToolType.fromItemStack(usedItem) == ToolType.PICKAXE
                e.player.damageItemStack(usedItem, 1)
                if (isRightTool && isRightTier) {
                    val moonstone = NexoItems.itemFromId(plugin.config.moonstoneItemId)?.build() ?: return@EventListener
                    location.world.dropItem(location.clone().add(0.5, 1.0, 0.5), moonstone)
                }
            }
        }
    }

    init {
        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                updateChunk(chunk)
            }
        }
        EventListener(ChunkLoadEvent::class.java) { e -> updateChunk(e.chunk) }
        handleMoonstones()
    }

}
