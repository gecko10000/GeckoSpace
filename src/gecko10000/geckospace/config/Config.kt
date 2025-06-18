@file:UseSerializers(MMComponentSerializer::class)

package gecko10000.geckospace.config

import com.charleskorn.kaml.YamlComment
import gecko10000.geckolib.config.serializers.MMComponentSerializer
import gecko10000.geckolib.extensions.parseMM
import gecko10000.geckospace.util.Dimension
import gecko10000.geckospace.util.ToolTier
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.EntityType

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
    val tntBedrockId: String = "tnt_bedrock",
    val bedrockBreakTntAmount: Int = 8,
    val terraPackIds: Map<String, String> = mapOf(
        "moon" to "moon",
    ),
    @YamlComment("Use MineSkin to generate: https://mineskin.org/skins/56be58e531194455a386458b270720fb")
    val herobrineSkinValue: String = "ewogICJ0aW1lc3RhbXAiIDogMTczMzYyMDMxMTQ5MSwKICAicHJvZmlsZUlkIiA6ICJhYWMxYjA2OWNkMjE0NWE2ODNlNzQxNzE4MDcxMGU4MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJqdXNhbXUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTJlMGU4ZTM2ZjY4ZDlhMDNlYjNhMjU3ZDAyZmY1Yjc5NDkxMWJlOTY3ZmFkZDU5Y2U1MTIwODU2ODVhMTIwOCIKICAgIH0KICB9Cn0=",
    val herobrineSkinSignature: String = "fVv7cyTo9DcrvAuvH39GsZgyE5WZprxm5OO0Q0ddALUUdLrx9D2wawCoGskT/Y1Lq3i0uaC6f9PvrrO5vZuqgxQG+Y51LuiwgjajfaMeSlqcyg4ul5SFlFGGlrW/zQZb9Y4B0mo6CCxbBZvi5mz5ZRTDBFg6r9OnBCM6Pwwklqlp5MWLlsu11RZLuEObg6di2Vt5k3oUbmmxWlnjfmVFDFHLw6XooUBJHbN4+4j2gMGMv7RAZblIDADOnbTBm48+QC6W7rzLQr1FZ5qiqxyvpcHfJyG2E7subw7CTxJuCJfJOkW7IIpt14Ykft13RVymfi3YOMh2tMEFILTVQc2+sKa7Hf67ODu+WW7u+/sD2sdHM2gxwlbrgevEBeOrXoEkJ8289LHh8pNx/U3O0izhKB814qmr6FKSWh4ejZB/KJQvl+U78WxYTxJeJuysvbIbfjWYpmOZfxY1b4DuKo/CPRS+tJXQwKQhlkUJa3Aw+ARDD/SDyk88oWFx9xrX70GrjPd2jtbEh25v+HWtrZ/HYVjjGPlo5Tb7XTfXTvkoXwLTYk5P3LhqrPqhlMdfEs2+aKa+1cxJjcNRtPrTmUtZHrwFZ0wrk8PDbbjvqgU49sKV2M9Y1NbqzbiDYdaQTX+C1Us3VVrAhRvqVeRAmKzVUeMQhEYJeQMxojiis6DWsIk=",
    @YamlComment("Multiplied by its proximity to the void [1,5]")
    val shrineEntityCount: Int = 5,
    val shrineEntities: Set<EntityType> = setOf(
        EntityType.WITHER_SKELETON,
        EntityType.PIGLIN_BRUTE,
        EntityType.BLAZE,
        EntityType.SKELETON,
        EntityType.ZOMBIFIED_PIGLIN,
        EntityType.MAGMA_CUBE,
    ),
    val netherWorldPairs: Map<String, String> = mapOf(
        "world" to "world_nether",
        "resource" to "resource_nether",
    ),
    val dimensionShifterItemId: String = "dimension_shifter",
    val resourceWorldPairs: Map<String, String> = mapOf(
        "world" to "resource",
        "world_nether" to "resource_nether",
    ),
    @YamlComment("Blocks to prevent teleportation to")
    val unsafeBlockTypes: Set<Material> = setOf(
        Material.CACTUS,
        Material.LAVA,
    ),
    val rocketId: String = "rocket",
    val rocketMenuMessage: Component = parseMM("<gold><b>Press <key:key.jump> to open the launch menu"),
    val rocketYOffset: Double = 1.0,
    val rocketSeatYOffset: Double = -0.5,
    val rocketLaunchHeight: Double = 80.0,
    val rocketMenuTitle: Component = parseMM("<#258bbe>Rocket"),
    val travelMenuTitle: Component = parseMM("<#258bbe>Travel"),
    val dimensions: Map<Dimension, DimensionInfo> = mapOf(
        Dimension.EARTH to DimensionInfo(35517, "world", parseMM("<gradient:#0A961E:#1B82D4>Earth")),
        Dimension.MOON to DimensionInfo(42221, "moon", parseMM("<gradient:grey:white>Moon")),
        Dimension.MARS to DimensionInfo(567, "mars", parseMM("<gradient:#8C4809:#BA6B13>Mars")),
    ),
)
