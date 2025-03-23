package gecko10000.geckospace.moon

import com.nexomc.nexo.api.NexoBlocks
import com.nexomc.nexo.api.NexoItems
import gecko10000.geckolib.blockdata.BlockDataManager
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import gecko10000.geckospace.util.ToolTier
import gecko10000.geckospace.util.ToolType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.block.Block
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.inject
import redempt.redlib.misc.EventListener
import redempt.redlib.misc.Task
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class MoonstoneManager : MyKoinComponent {

    private val plugin: GeckoSpace by inject()

    private val bdm = BlockDataManager("ms", PersistentDataType.LONG)

    private fun growMoonstone(block: Block) {
        bdm.remove(block)
        NexoBlocks.place(plugin.config.moonstoneFullId, block.location)
    }

    // TODO: optimize?
    private fun tickEmptyMoonstones() {
        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                bdm.getValuedBlocks(chunk).forEach {
                    bdm[it] = System.currentTimeMillis()
                    val rarity = plugin.config.moonstoneGrowRarity
                    val didGrow = Random.nextInt(rarity) == 0
                    if (!didGrow) return@forEach
                    growMoonstone(it)
                }
            }
        }
    }

    private fun catchup(block: Block) {
        val lastUpdate = bdm.getValue(block)
        val minutesPassed =
            ((System.currentTimeMillis() - lastUpdate) / 1000 / plugin.config.moonstoneGrowSeconds).toInt()
        val noGrowChance = BigDecimal(1 - 1.0 / plugin.config.moonstoneGrowRarity)
            .pow(minutesPassed).toDouble()
        val growChance = 1 - noGrowChance
        val didGrow = Random.nextFloat() < growChance
        if (didGrow) {
            growMoonstone(block)
        }
    }

    private fun updateChunk(chunk: Chunk) {
        bdm.getValuedBlocks(chunk).forEach(::catchup)
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            while (plugin.isEnabled) {
                delay(plugin.config.moonstoneGrowSeconds.seconds)
                Task.syncDelayed { -> tickEmptyMoonstones() }
            }
        }
        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                updateChunk(chunk)
            }
        }
        EventListener(ChunkLoadEvent::class.java) { e -> updateChunk(e.chunk) }
        EventListener(BlockBreakEvent::class.java) { e ->
            if (e.isCancelled) return@EventListener
            val location = e.block.location
            val nexoId = NexoBlocks.customBlockMechanic(location)?.itemID ?: return@EventListener
            // Prevent breaking empty moonstones
            if (nexoId == plugin.config.moonstoneEmptyId) {
                e.isCancelled = true
                if (!bdm.contains(e.block)) {
                    bdm[e.block] = System.currentTimeMillis()
                }
                return@EventListener
            }
            // Break full ones by replacing with an empty one
            // and dropping the item if mined with the right block.
            if (nexoId == plugin.config.moonstoneFullId) {
                e.isCancelled = true
                NexoBlocks.place(plugin.config.moonstoneEmptyId, location)
                bdm[e.block] = System.currentTimeMillis()

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

}
