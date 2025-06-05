package gecko10000.geckospace.dimensions.mantle

import com.nexomc.nexo.api.NexoBlocks
import gecko10000.geckolib.blockdata.BlockDataManager
import gecko10000.geckolib.extensions.name
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.inject
import redempt.redlib.misc.Task

class BedrockBlowUpManager : MyKoinComponent, Listener {

    private val plugin: GeckoSpace by inject()

    // Stores number of TNT used on cracked bedrock.
    private val crackedBedrockTracker = BlockDataManager("cbdrk", PersistentDataType.INTEGER, events = false)

    @EventHandler(priority = EventPriority.HIGH)
    private fun PlayerInteractEvent.onPackTNTIntoCrackedBedrock() {
        if (useItemInHand() == Event.Result.DENY) return
        if (action != Action.RIGHT_CLICK_BLOCK) return
        if (player.isSneaking) return
        val block = clickedBlock ?: return
        if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.crackedBedrockId) {
            return
        }
        val item = item
        if (item == null || item.type != Material.TNT) {
            player.sendRichMessage(
                "<red>Use <item> to remove this bedrock.",
                Placeholder.component("item", ItemStack.of(Material.TNT).name())
            )
            return
        }
        isCancelled = true
        item.amount--
        val prevValue = crackedBedrockTracker[block] ?: 0
        val newValue = prevValue + 1
        if (newValue >= plugin.config.bedrockBreakTntAmount) {
            crackedBedrockTracker.remove(block)
            NexoBlocks.place(plugin.config.tntBedrockId, block.location)
        } else {
            crackedBedrockTracker[block] = newValue
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private fun PlayerInteractEvent.onTNTBedrockIgnite() {
        if (useItemInHand() == Event.Result.DENY) return
        if (action != Action.RIGHT_CLICK_BLOCK) return
        val block = clickedBlock ?: return
        if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.tntBedrockId) {
            return
        }
        val item = item ?: return
        if (item.type == Material.TNT && !player.isSneaking) {
            isCancelled = true
            return
        }
        if (item.type != Material.FLINT_AND_STEEL) return
        Task.syncDelayed { -> blowUpBedrock(block) }
    }

    @EventHandler
    private fun ProjectileHitEvent.onFireArrowLand() {
        val block = hitBlock ?: return
        if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.tntBedrockId) {
            return
        }
        if (entity.fireTicks <= 0) {
            return
        }
        Task.syncDelayed { -> blowUpBedrock(block) }
    }

    private fun blowUpBedrock(block: Block) {
        val onBottom = block.y == block.world.minHeight
        val newType = if (onBottom) Material.END_PORTAL else Material.AIR
        block.type = newType
        block.world.createExplosion(block.location.toCenterLocation(), 10f)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun BlockExplodeEvent.onBlockExplode() {
        for (block in blockList()) {
            if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.tntBedrockId) continue
            blowUpBedrock(block)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun EntityExplodeEvent.onEntityExplode() {
        for (block in blockList()) {
            if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.tntBedrockId) continue
            blowUpBedrock(block)
        }
    }

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

}
