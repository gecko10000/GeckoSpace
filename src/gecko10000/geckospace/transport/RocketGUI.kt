package gecko10000.geckospace.transport

import gecko10000.geckolib.extensions.parseMM
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.inject
import redempt.redlib.inventorygui.InventoryGUI
import redempt.redlib.inventorygui.ItemButton

class RocketGUI(player: Player, rocketEntity: ItemDisplay) : RocketAssociatedGUI(player, rocketEntity),
    MyKoinComponent {

    private companion object {
        const val SIZE = 27
    }

    private val plugin: GeckoSpace by inject()

    private fun travelButton(): ItemButton {
        val item = ItemStack.of(Material.ELYTRA)
        val meta = item.itemMeta
        meta.displayName(parseMM("<green><b>Fly"))
        item.itemMeta = meta
        return ItemButton.create(item) { e ->
            TravelGUI(player, rocketEntity)
        }
    }

    override fun createInventory(): InventoryGUI {
        val inventory = InventoryGUI(Bukkit.createInventory(this, SIZE, plugin.config.rocketMenuTitle))
        inventory.fill(0, SIZE, FILLER)
        inventory.addButton(travelButton(), 11)
        return inventory
    }

}
