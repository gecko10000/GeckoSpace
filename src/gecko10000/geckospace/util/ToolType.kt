package gecko10000.geckospace.util

import org.bukkit.inventory.ItemStack

enum class ToolType {
    AXE,
    PICKAXE,
    SHOVEL,
    SWORD,
    HOE,
    ;

    companion object {
        fun fromItemStack(item: ItemStack): ToolType? {
            val name = item.type.name
            for (type in ToolType.entries) {
                if (name.endsWith("_$type")) {
                    return type
                }
            }
            return null
        }
    }
}
