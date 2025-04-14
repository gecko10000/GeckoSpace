package gecko10000.geckospace.di

import gecko10000.geckospace.CommandHandler
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.dimensions.mantle.MantleManager
import gecko10000.geckospace.dimensions.moon.MoonstoneManager
import gecko10000.geckospace.dimensions.resource.ShifterManager
import org.koin.dsl.module

fun pluginModules(plugin: GeckoSpace) = module {
    single { plugin }
    single(createdAtStart = true) { CommandHandler() }
    single(createdAtStart = true) { MoonstoneManager() }
    single(createdAtStart = true) { MantleManager() }
    single(createdAtStart = true) { ShifterManager() }
}
