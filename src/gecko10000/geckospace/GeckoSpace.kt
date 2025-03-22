package gecko10000.geckospace

import gecko10000.geckolib.config.YamlFileManager
import gecko10000.geckospace.config.Config
import gecko10000.geckospace.di.MyKoinContext
import org.bukkit.plugin.java.JavaPlugin

class GeckoSpace : JavaPlugin() {

    private val configFile = YamlFileManager(
        configDirectory = dataFolder,
        initialValue = Config(),
        serializer = Config.serializer(),
    )

    val config: Config
        get() = configFile.value

    override fun onEnable() {
        MyKoinContext.init(this)
    }

    fun reloadConfigs() {
        configFile.reload()
    }

}
