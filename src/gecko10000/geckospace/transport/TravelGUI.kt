package gecko10000.geckospace.transport

import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import gecko10000.geckospace.util.Dimension
import me.arcaniax.hdb.api.HeadDatabaseAPI
import org.bukkit.Bukkit
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.koin.core.component.inject
import redempt.redlib.inventorygui.InventoryGUI
import redempt.redlib.inventorygui.ItemButton
import redempt.redlib.itemutils.ItemUtils
import kotlin.math.min

class TravelGUI(player: Player, rocketEntity: ItemDisplay) :
    RocketAssociatedGUI(player, rocketEntity),
    MyKoinComponent {

    private val plugin: GeckoSpace by inject()
    private val rocketManager: RocketManager by inject()
    private val hdbAPI: HeadDatabaseAPI by inject()

    private fun travelButton(dimension: Dimension): ItemButton? {
        val info = plugin.config.dimensions[dimension] ?: return null
        val item = hdbAPI.getItemHead(info.headId.toString())
        val meta = item.itemMeta
        meta.displayName(info.uiTitle)
        item.itemMeta = meta
        return ItemButton.create(item) { e ->
            player.closeInventory()
            rocketManager.launch(player, rocketEntity, dimension)
        }
    }

    override fun createInventory(): InventoryGUI {
        val unlocked = 10 // TODO: permissions?
        val size = min(54, ItemUtils.minimumChestSize(unlocked) + 9)
        val inventory = InventoryGUI(Bukkit.createInventory(this, size, plugin.config.travelMenuTitle))
        inventory.fill(0, size, FILLER)
        var i = 0
        for (dimension in Dimension.entries) {
            val button = travelButton(dimension) ?: continue
            inventory.addButton(button, i++)
        }
        inventory.addButton(BACK { RocketGUI(player, rocketEntity) }, size - 9)
        return inventory
    }
}
