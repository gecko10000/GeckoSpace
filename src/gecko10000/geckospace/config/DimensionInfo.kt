@file:UseSerializers(MMComponentSerializer::class)

package gecko10000.geckospace.config

import gecko10000.geckolib.config.serializers.MMComponentSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.kyori.adventure.text.Component

@Serializable
data class DimensionInfo(
    val headId: Int,
    val worldName: String,
    val uiTitle: Component,
)
