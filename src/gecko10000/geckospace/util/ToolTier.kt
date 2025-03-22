package gecko10000.geckospace.util

import org.bukkit.Material.*
import org.bukkit.inventory.ItemStack

enum class ToolTier {
    WOOD,
    GOLD,
    STONE,
    IRON,
    DIAMOND,
    NETHERITE,
    ;

    fun isAtLeast(tier: ToolTier): Boolean {
        return this.ordinal >= tier.ordinal
    }

    companion object {
        fun fromItemStack(item: ItemStack): ToolTier? {
            return when (item.type) {
                WOODEN_SWORD, WOODEN_SHOVEL, WOODEN_PICKAXE, WOODEN_AXE, WOODEN_HOE -> WOOD
                GOLDEN_SWORD, GOLDEN_SHOVEL, GOLDEN_PICKAXE, GOLDEN_AXE, GOLDEN_HOE -> GOLD
                STONE_SWORD, STONE_SHOVEL, STONE_PICKAXE, STONE_AXE, STONE_HOE -> STONE
                IRON_SWORD, IRON_SHOVEL, IRON_PICKAXE, IRON_AXE, IRON_HOE -> IRON
                DIAMOND_SWORD, DIAMOND_SHOVEL, DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_HOE -> DIAMOND
                NETHERITE_SWORD, NETHERITE_SHOVEL, NETHERITE_PICKAXE, NETHERITE_AXE, NETHERITE_HOE -> NETHERITE
                else -> null
            }
        }
    }

}
