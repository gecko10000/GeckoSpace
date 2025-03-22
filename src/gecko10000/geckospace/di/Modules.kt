package gecko10000.geckospace.di

import gecko10000.geckospace.CommandHandler
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.moon.MoonManager
import org.koin.dsl.module

fun pluginModules(plugin: GeckoSpace) = module {
    single { plugin }
    single(createdAtStart = true) { CommandHandler() }
    single(createdAtStart = true) { MoonManager() }
}
