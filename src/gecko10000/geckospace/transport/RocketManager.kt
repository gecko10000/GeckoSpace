package gecko10000.geckospace.transport

import com.destroystokyo.paper.ParticleBuilder
import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent
import com.nexomc.nexo.mechanics.furniture.FurnitureHelpers
import com.nexomc.nexo.mechanics.furniture.seats.FurnitureSeat
import com.nexomc.nexo.utils.drops.Drop
import gecko10000.geckolib.extensions.parseMM
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import gecko10000.geckospace.util.Dimension
import io.papermc.paper.entity.TeleportFlag
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.inject
import redempt.redlib.misc.Task
import java.util.*
import kotlin.random.Random

class RocketManager : MyKoinComponent, Listener {

    private companion object {
        const val DURATION = 5
        const val PARTICLE_HORIZ_OFFSET = 0.38
        const val PARTICLE_VERT_OFFSET = -1.5
        const val RANDOM_FLAME_OFFSET_RANGE = 0.1
        const val PARTICLE_VELOCITY_MULT = 0.02
        const val LIFTOFF_MULTIPLIER = 0.01
        val SEAT_KEY = NamespacedKey("geckospace", "rocket_seat")
        val TARGET_DIMENSION_KEY = NamespacedKey("geckospace", "target_dimension")
    }

    private val plugin: GeckoSpace by inject()

    private val loadedRockets = mutableMapOf<UUID, ItemDisplay>()
    private val launchingSeats = mutableSetOf<TextDisplay>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    // Shows actionbar after getting in
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    private fun EntityMountEvent.onMountRocket() {
        val vehicle = this.mount
        val furniture = NexoFurniture.furnitureMechanic(vehicle.location) ?: return
        if (furniture.itemID != plugin.config.rocketId) return
        Task.syncDelayed { ->
            entity.sendActionBar(plugin.config.rocketMenuMessage)
        }
    }

    // Opens rocket GUI when seated
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    private fun PlayerInputEvent.onJumpInRocket() {
        val vehicle = player.vehicle ?: return
        FurnitureSeat.getSeat(vehicle)
        val furniture = NexoFurniture.furnitureMechanic(vehicle.location) ?: return
        if (furniture.itemID != plugin.config.rocketId) return
        val prevInput = player.currentInput
        val newInput = input
        if (prevInput.isJump || !newInput.isJump) return
        val baseEntity = NexoFurniture.baseEntity(vehicle.location) ?: return
        RocketGUI(player, baseEntity)
    }

    // Show smoke if ready to launch
    @EventHandler
    private fun NexoFurniturePlaceEvent.onRocketPlace() {
        if (mechanic.itemID != plugin.config.rocketId) return

    }

    // Prevent GUI use after rocket break
    @EventHandler
    private fun NexoFurnitureBreakEvent.onRocketBreak() {
        plugin.server.onlinePlayers.forEach { p ->
            val openInv = p.openInventory.topInventory.holder
            if (openInv !is RocketAssociatedGUI) return@forEach
            if (openInv.rocketEntity.uniqueId != baseEntity.uniqueId) return@forEach
            p.closeInventory()
        }
    }

    private fun doRocketParticles(location: Location, i: Int) {
        val center = location.clone().add(0.0, PARTICLE_VERT_OFFSET, 0.0)

        for (unused in 1..5) {
            val xOffset = Random.nextDouble() * (RANDOM_FLAME_OFFSET_RANGE * 2) - RANDOM_FLAME_OFFSET_RANGE
            val zOffset = Random.nextDouble() * (RANDOM_FLAME_OFFSET_RANGE * 2) - RANDOM_FLAME_OFFSET_RANGE
            ParticleBuilder(Particle.FLAME)
                .count(0)
                .offset(xOffset, -(i * PARTICLE_VELOCITY_MULT), zOffset)
                .location(center.clone().add(PARTICLE_HORIZ_OFFSET, 0.0, 0.0))
                .spawn()
                .location(center.clone().add(-PARTICLE_HORIZ_OFFSET, 0.0, 0.0))
                .spawn()
                .location(center.clone().add(0.0, 0.0, PARTICLE_HORIZ_OFFSET))
                .spawn()
                .location(center.clone().add(0.0, 0.0, -PARTICLE_HORIZ_OFFSET))
                .spawn()
        }
    }

    private fun finishRocket(player: Player) {
        if (!player.isOnline) return
        val dimensionName =
            player.persistentDataContainer.get(TARGET_DIMENSION_KEY, PersistentDataType.STRING)
                ?: return
        val targetWorldName = plugin.config.dimensions[Dimension.valueOf(dimensionName)]?.worldName ?: return
        val targetWorld = plugin.server.getWorld(targetWorldName)
        if (targetWorld == null) {
            plugin.logger.severe("Could not teleport ${player.name} to $targetWorldName (world not found)")
            return
        }
        player.persistentDataContainer.remove(TARGET_DIMENSION_KEY)
        val destination = player.location
        destination.world = targetWorld
        player.teleportAsync(destination)
    }

    fun launch(player: Player, baseEntity: ItemDisplay, destination: Dimension) {
        val location = baseEntity.location
        val startingY = location.y
        if (location.world.getHighestBlockYAt(location) >= startingY.toInt()) {
            player.showTitle(Title.title(parseMM("<red>The sky is blocked!"), Component.empty()))
            player.sendMessage(parseMM("<red>Clear a path to the sky to launch!"))
            player.leaveVehicle()
            return
        }
        val rocketItem = FurnitureHelpers.furnitureItem(baseEntity)
        NexoFurniture.remove(
            baseEntity, drop = Drop(
                mutableListOf(),
                silktouch = false,
                fortune = false,
                sourceID = ""
            )
        )
        val display = location.world.spawn(
            location.clone().add(0.0, plugin.config.rocketYOffset, 0.0),
            ItemDisplay::class.java
        ) { i ->
            i.isPersistent = false
            i.teleportDuration = DURATION
            i.setItemStack(rocketItem)
        }
        val seat = location.world.spawn(
            location.clone().add(0.0, plugin.config.rocketSeatYOffset, 0.0),
            TextDisplay::class.java
        ) { t ->
            t.isPersistent = false
            t.teleportDuration = DURATION
            t.persistentDataContainer.set(SEAT_KEY, PersistentDataType.STRING, player.uniqueId.toString())
        }
        seat.addPassenger(player)
        player.persistentDataContainer.set(TARGET_DIMENSION_KEY, PersistentDataType.STRING, destination.toString())
        var i = 0
        val interpolatedLocation: Location = display.location
        // Updates location to show particles at, and shows them
        val particleTask = Task.syncRepeating({ ->
            doRocketParticles(interpolatedLocation, i)
            val diff = (i - 1) * LIFTOFF_MULTIPLIER
            interpolatedLocation.add(0.0, diff, 0.0)

        }, 1L, 1L)
        Task.syncRepeating({ t ->
            val diff = DURATION * i++ * LIFTOFF_MULTIPLIER
            display.teleportAsync(display.location.add(0.0, diff, 0.0))
            seat.teleportAsync(
                seat.location.add(0.0, diff, 0.0),
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                TeleportFlag.EntityState.RETAIN_PASSENGERS
            )
            if (display.location.y > startingY + plugin.config.rocketLaunchHeight) {
                display.remove()
                seat.remove()
                t.cancel()
                particleTask.cancel()
                finishRocket(player)
            }
        }, 0L, DURATION.toLong())
        launchingSeats += seat
    }

    @EventHandler
    private fun PlayerJoinEvent.onJoinDuringLaunch() {
        val intendedSeat = launchingSeats.firstOrNull {
            it.persistentDataContainer.get(
                SEAT_KEY,
                PersistentDataType.STRING
            ) == player.uniqueId.toString()
        }
        // Rocket animation finished, player still needs to be teleported to destination.
        if (intendedSeat == null && player.persistentDataContainer.has(TARGET_DIMENSION_KEY)) {
            finishRocket(player)
            return
        }
        // Rocket animation not done, re-seat player.
        intendedSeat?.addPassenger(player)
    }

    private var ignoreDismount = false

    @EventHandler
    private fun EntityDismountEvent.onDismountLaunchingRocket() {
        val seat = this.dismounted
        if (!seat.persistentDataContainer.has(SEAT_KEY)) return
        if (ignoreDismount) return
        isCancelled = true
    }

    @EventHandler
    private fun PlayerQuitEvent.ignoreDisconnectDismount() {
        ignoreDismount = true
        player.leaveVehicle()
        ignoreDismount = false
    }

}
