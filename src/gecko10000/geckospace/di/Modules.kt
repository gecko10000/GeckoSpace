package gecko10000.geckospace.di

import gecko10000.geckospace.CommandHandler
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.dimensions.mantle.BedrockBlowUpManager
import gecko10000.geckospace.dimensions.mantle.ShrineManager
import gecko10000.geckospace.dimensions.mantle.TeleportationManager
import gecko10000.geckospace.dimensions.moon.MoonstoneManager
import gecko10000.geckospace.dimensions.resource.ShifterManager
import gecko10000.geckospace.transport.RocketManager
import me.arcaniax.hdb.api.HeadDatabaseAPI
import org.koin.dsl.module

fun pluginModules(plugin: GeckoSpace) = module {
    single { plugin }
    single(createdAtStart = true) { CommandHandler() }
    // Moon
    single(createdAtStart = true) { MoonstoneManager() }
    // Mantle
    single(createdAtStart = true) { ShrineManager() }
    single(createdAtStart = true) { BedrockBlowUpManager() }
    single(createdAtStart = true) { TeleportationManager() }
    // Resource worlds
    single(createdAtStart = true) { ShifterManager() }

    single(createdAtStart = true) { RocketManager() }
    single { HeadDatabaseAPI() }
}
