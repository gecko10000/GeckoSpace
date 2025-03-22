package gecko10000.geckospace.config

import gecko10000.geckospace.util.ToolTier
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val moonstoneFullId: String = "moonstone_ore_full",
    val moonstoneEmptyId: String = "moonstone_ore_empty",
    val moonstoneItemId: String = "moonstone",
    val moonstoneMinTier: ToolTier = ToolTier.WOOD,
)
