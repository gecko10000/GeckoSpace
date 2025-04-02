package gecko10000.geckospace.config

import com.charleskorn.kaml.YamlComment
import gecko10000.geckospace.util.ToolTier
import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val moonstoneFullId: String = "moonstone_ore_full",
    val moonstoneEmptyId: String = "moonstone_ore_empty",
    val moonstoneItemId: String = "moonstone",
    val moonstoneMinTier: ToolTier = ToolTier.WOOD,
    @YamlComment(
        "How often moonstone blocks are",
        "ticked, including when unloaded.",
    )
    val moonstoneGrowSeconds: Int = 60,
    @YamlComment(
        "Moonstone blocks are ticked every",
        "minute (including when unloaded).",
        "Chance for growth is 1/<moonstone-grow-rarity>."
    )
    val moonstoneGrowRarity: Int = 1000,
    val crackedBedrockId: String = "cracked_bedrock",
    val bedrockBreakTntAmount: Int = 8,
    val terraPackIds: Map<String, String> = mapOf(
        "moon" to "moon",
    )
)
