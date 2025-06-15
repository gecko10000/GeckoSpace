package gecko10000.geckospace.transport

import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.player.PlayerInputEvent
import org.koin.core.component.inject
import redempt.redlib.misc.Task

class RocketManager : MyKoinComponent, Listener {

    private val plugin: GeckoSpace by inject()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // Shows actionbar after getting in
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    private fun EntityMountEvent.onMountRocket() {
        val vehicle = this.mount
        val furniture = NexoFurniture.furnitureMechanic(vehicle.location) ?: return
        if (furniture.itemID != plugin.config.rocketId) return
        Task.syncDelayed { ->
            entity.sendActionBar(plugin.config.rocketMenuMessage)
        }
    }

    // Opens rocket GUI when seated
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    private fun PlayerInputEvent.onJumpInRocket() {
        val vehicle = player.vehicle ?: return
        val furniture = NexoFurniture.furnitureMechanic(vehicle.location) ?: return
        if (furniture.itemID != plugin.config.rocketId) return
        val prevInput = player.currentInput
        val newInput = input
        if (prevInput.isJump || !newInput.isJump) return
        RocketGUI(player, NexoFurniture.baseEntity(vehicle.location)!!)
    }

    // Prevent GUI use after rocket break
    @EventHandler
    private fun NexoFurnitureBreakEvent.onRocketBreak() {
        plugin.server.onlinePlayers.forEach { p ->
            val openInv = p.openInventory.topInventory.holder
            if (openInv !is RocketAssociatedGUI) return@forEach
            if (openInv.rocketEntity.uniqueId != baseEntity.uniqueId) return@forEach
            p.closeInventory()
        }
    }

}
