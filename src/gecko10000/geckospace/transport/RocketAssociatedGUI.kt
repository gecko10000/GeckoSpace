package gecko10000.geckospace.transport

import gecko10000.geckolib.GUI
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player

abstract class RocketAssociatedGUI(player: Player, val rocketEntity: ItemDisplay) : GUI(player)
