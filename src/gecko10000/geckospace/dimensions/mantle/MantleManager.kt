package gecko10000.geckospace.dimensions.mantle

import com.nexomc.nexo.api.NexoBlocks
import gecko10000.geckolib.extensions.name
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.koin.core.component.inject
import redempt.redlib.misc.EventListener
import redempt.redlib.misc.Task

// Mantle is just the nether,
// with custom entry/exit mechanics.
class MantleManager : MyKoinComponent {

    private val plugin: GeckoSpace by inject()


    private fun startHerobrine(shrineChecker: ShrineChecker) {
        
    }

    private fun crackBedrock(blocks: Set<Block>) {
        blocks.forEach { block ->
            NexoBlocks.place(plugin.config.crackedBedrockId, block.location)
            block.world.strikeLightningEffect(block.location.add(0.5, 0.0, 0.5))
        }
    }

    private fun breakCrackedBedrock(block: Block) {
        val onBottom = block.y == block.world.minHeight
        val newType = if (onBottom) Material.END_PORTAL else Material.AIR
        block.type = newType
        if (onBottom) {
            block.world.playSound(
                block.location.add(0.5, 0.0, 0.5),
                Sound.BLOCK_PORTAL_TRIGGER,
                SoundCategory.BLOCKS,
                1f,
                1f
            )
        }
    }

    init {
        EventListener(PlayerInteractEvent::class.java, EventPriority.MONITOR) { e ->
            if (e.useItemInHand() == Event.Result.DENY) return@EventListener
            val block = e.clickedBlock
            if (e.action != Action.RIGHT_CLICK_BLOCK) return@EventListener
            if (block == null || block.type != Material.NETHERRACK) return@EventListener
            if (e.blockFace != BlockFace.UP) return@EventListener
            val item = e.item ?: return@EventListener
            if (item.type != Material.FLINT_AND_STEEL) return@EventListener
            val shrineChecker = ShrineChecker(block.getRelative(BlockFace.UP))
            if (!shrineChecker.isValid) return@EventListener
            if (shrineChecker.getBedrockBlocksToCrack().isEmpty()) {
                e.player.sendRichMessage("<red>This only works over bedrock...")
                return@EventListener
            }
            startHerobrine(shrineChecker)
        }
        EventListener(PlayerInteractEvent::class.java, EventPriority.HIGH) { e ->
            if (e.useItemInHand() == Event.Result.DENY) return@EventListener
            if (e.action != Action.RIGHT_CLICK_BLOCK) return@EventListener
            val block = e.clickedBlock ?: return@EventListener
            if (block.type != Material.NOTE_BLOCK || NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.crackedBedrockId) {
                return@EventListener
            }
            e.isCancelled = true
            val item = e.item
            if (item == null || item.type != Material.TNT || item.amount < plugin.config.bedrockBreakTntAmount) {
                e.player.sendRichMessage(
                    "<red>Use <amount> <item> to remove this bedrock.",
                    Placeholder.unparsed("amount", plugin.config.bedrockBreakTntAmount.toString()),
                    Placeholder.component("item", ItemStack.of(Material.TNT).name())
                )
                return@EventListener
            }
            item.amount -= plugin.config.bedrockBreakTntAmount
            Task.syncDelayed { ->
                breakCrackedBedrock(block)
            }
        }
    }

}
