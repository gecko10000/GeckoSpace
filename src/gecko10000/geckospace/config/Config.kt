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
    ),
    @YamlComment("Use MineSkin to generate: https://mineskin.org/skins/56be58e531194455a386458b270720fb")
    val herobrineSkinValue: String = "ewogICJ0aW1lc3RhbXAiIDogMTczMzYyMDMxMTQ5MSwKICAicHJvZmlsZUlkIiA6ICJhYWMxYjA2OWNkMjE0NWE2ODNlNzQxNzE4MDcxMGU4MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJqdXNhbXUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTJlMGU4ZTM2ZjY4ZDlhMDNlYjNhMjU3ZDAyZmY1Yjc5NDkxMWJlOTY3ZmFkZDU5Y2U1MTIwODU2ODVhMTIwOCIKICAgIH0KICB9Cn0=",
    val herobrineSkinSignature: String = "fVv7cyTo9DcrvAuvH39GsZgyE5WZprxm5OO0Q0ddALUUdLrx9D2wawCoGskT/Y1Lq3i0uaC6f9PvrrO5vZuqgxQG+Y51LuiwgjajfaMeSlqcyg4ul5SFlFGGlrW/zQZb9Y4B0mo6CCxbBZvi5mz5ZRTDBFg6r9OnBCM6Pwwklqlp5MWLlsu11RZLuEObg6di2Vt5k3oUbmmxWlnjfmVFDFHLw6XooUBJHbN4+4j2gMGMv7RAZblIDADOnbTBm48+QC6W7rzLQr1FZ5qiqxyvpcHfJyG2E7subw7CTxJuCJfJOkW7IIpt14Ykft13RVymfi3YOMh2tMEFILTVQc2+sKa7Hf67ODu+WW7u+/sD2sdHM2gxwlbrgevEBeOrXoEkJ8289LHh8pNx/U3O0izhKB814qmr6FKSWh4ejZB/KJQvl+U78WxYTxJeJuysvbIbfjWYpmOZfxY1b4DuKo/CPRS+tJXQwKQhlkUJa3Aw+ARDD/SDyk88oWFx9xrX70GrjPd2jtbEh25v+HWtrZ/HYVjjGPlo5Tb7XTfXTvkoXwLTYk5P3LhqrPqhlMdfEs2+aKa+1cxJjcNRtPrTmUtZHrwFZ0wrk8PDbbjvqgU49sKV2M9Y1NbqzbiDYdaQTX+C1Us3VVrAhRvqVeRAmKzVUeMQhEYJeQMxojiis6DWsIk=",
)
