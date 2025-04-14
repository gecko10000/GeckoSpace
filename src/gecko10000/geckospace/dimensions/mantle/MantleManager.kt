package gecko10000.geckospace.dimensions.mantle

import com.nexomc.nexo.api.NexoBlocks
import gecko10000.geckolib.blockdata.BlockDataManager
import gecko10000.geckolib.extensions.name
import gecko10000.geckolib.playerplaced.PlayerPlacedBlockTracker
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import io.papermc.paper.entity.LookAnchor
import io.papermc.paper.event.entity.EntityInsideBlockEvent
import io.papermc.paper.event.entity.EntityMoveEvent
import io.papermc.paper.math.BlockPosition
import io.papermc.paper.math.Position
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.trait.SkinTrait
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.*
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.PortalCreateEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.koin.core.component.inject
import redempt.redlib.misc.Task
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.collections.ArrayDeque
import kotlin.math.ceil
import kotlin.random.Random

// Mantle is just the nether,
// with custom entry/exit mechanics.
@Suppress("UnstableApiUsage")
class MantleManager : MyKoinComponent, Listener {

    private val plugin: GeckoSpace by inject()

    // START OF BEDROCK CRACKING

    @EventHandler(priority = EventPriority.MONITOR)
    private fun PlayerInteractEvent.onIgniteShrine() {
        if (useItemInHand() == Event.Result.DENY) return
        if (action != Action.RIGHT_CLICK_BLOCK) return
        val block = clickedBlock
        if (block == null || block.type != Material.NETHERRACK) return
        if (blockFace != BlockFace.UP) return
        if (item?.type != Material.FLINT_AND_STEEL) return
        val shrineChecker = ShrineChecker(block.getRelative(BlockFace.UP), player)
        if (!shrineChecker.isValid) return
        val isValidWorld = plugin.config.netherWorldPairs.any { it.key == block.world.name }
        if (!isValidWorld) {
            return
        }
        if (shrineChecker.getBedrockBlocksToCrack().isEmpty()) {
            player.sendRichMessage("<red>This only works over bedrock...")
            return
        }
        shrineSeq1(shrineChecker)
    }

    // Mobs are spawned based on y level (more if lower -- there are 5 layers of bedrock)
    // blocks converted: gold -> sand, red torch -> torch, netherrack -> obsidian
    // When shrine is on last layer (bedrock on y=1),
    // Herobrine spawns for a split second
    // TODO: kill player and play enderman sound if process interrupted (i.e. unexpected block found)
    private fun shrineSeq1(shrineChecker: ShrineChecker) {
        // TODO: make the blocks temporarily immovable/unbreakable
        val nearbyPlayers = shrineChecker.getNearbyPlayers(25.0)
        // sound count depends on how low the shrine is (1-5 depending on layer)
        val soundCount = shrineChecker.closenessToVoid
        for (player in nearbyPlayers) {
            for (i in 1..soundCount) {
                player.playSound(player, Sound.AMBIENT_CAVE, 1f, 0.5f)
            }
        }
        val runnable = if (shrineChecker.blocksToVoid == 0) {
            val npc = prepHerobrine(shrineChecker);
            Runnable { shrineSeq2(shrineChecker, npc) }
        } else {
            Runnable { shrineFinal(shrineChecker) }
        }
        Task.syncDelayed(runnable, 100)
    }

    private fun shrineSeq2(shrineChecker: ShrineChecker, npc: NPC) {
        shrineChecker.world.strikeLightning(shrineChecker.fireBlock.location.add(0.5, 0.0, 0.5))
        npc.teleport(shrineChecker.fireBlock.location.add(0.5, 0.0, 0.5), PlayerTeleportEvent.TeleportCause.PLUGIN)
        try {
            npc.entity.lookAt(shrineChecker.lighter.eyeLocation, LookAnchor.EYES)
        } finally {
            Task.syncDelayed({ ->
                CitizensAPI.getNPCRegistry().deregister(npc)
                shrineChecker.world.strikeLightningEffect(shrineChecker.fireBlock.location.add(0.5, 0.0, 0.5))
                shrineFinal(shrineChecker)
            }, 20)
        }
    }

    private fun prepHerobrine(shrineChecker: ShrineChecker): NPC {
        val npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Herobrine")
        val spawnLocation = shrineChecker.fireBlock.location
        spawnLocation.y = shrineChecker.world.minHeight - 100.0 // Ensure it's not visible yet
        npc.spawn(spawnLocation)
        val skinTrait = npc.getOrAddTrait(SkinTrait::class.java)
        skinTrait.setSkinPersistent("herobrine", plugin.config.herobrineSkinSignature, plugin.config.herobrineSkinValue)
        npc.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, "false")
        return npc
    }

    private fun shrineFinal(shrineChecker: ShrineChecker) {
        val blocksToReplace = shrineChecker.getShrineBlocks()
        for (block in blocksToReplace) {
            val newType = when (block.type) {
                Material.GOLD_BLOCK -> Material.SAND
                Material.REDSTONE_TORCH -> Material.TORCH
                Material.NETHERRACK -> Material.OBSIDIAN
                Material.FIRE -> Material.AIR
                else -> block.type
            }
            block.setType(newType, false)
        }
        // Clear out deepslate, tuff, and ores around shrine to maximize deadliness
        for (x in -3..3) {
            for (y in -1..3) {
                for (z in -3..3) {
                    val block = shrineChecker.fireBlock.getRelative(x, y, z)
                    if (block.type == Material.BEDROCK || block.type == Material.END_PORTAL) continue
                    if (NexoBlocks.noteBlockMechanic(block)?.itemID == plugin.config.crackedBedrockId) continue
                    if (block in shrineChecker.getShrineBlocks()) continue
                    block.type = Material.AIR
                }
            }
        }

        // random between [1, dist]
        val nearbyPlayers = shrineChecker.getNearbyPlayers(25.0)
        val entityMultiplier = Random.nextInt(shrineChecker.closenessToVoid) + 1 // [0, 5 - [0,4]) + 1
        for (i in 1..entityMultiplier) {
            for (j in 1..plugin.config.shrineEntityCount) {
                val type = plugin.config.shrineEntities.random()
                val spawnLocation = shrineChecker.findRandomTopBlockAroundShrine()
                    .getRelative(BlockFace.UP)
                    .location
                    .add(0.5, 0.0, 0.5)
                val entity = shrineChecker.world.spawnEntity(spawnLocation, type)
                (entity as? MagmaCube)?.size = 3
                (entity as? Ghast)?.let {
                    val scaleAttr = it.getAttribute(Attribute.SCALE)!!
                    scaleAttr.addModifier(
                        AttributeModifier(
                            NamespacedKey(plugin, "scale"),
                            0.25 - 1,
                            AttributeModifier.Operation.MULTIPLY_SCALAR_1
                        )
                    )
                }
                (entity as? Mob)?.target = nearbyPlayers.randomOrNull()

            }
        }
        crackBedrock(shrineChecker.getBedrockBlocksToCrack())
    }

    private fun crackBedrock(blocks: Set<Block>) {
        blocks.forEach { block ->
            NexoBlocks.place(plugin.config.crackedBedrockId, block.location)
            block.world.strikeLightningEffect(block.location.add(0.5, 0.0, 0.5))
        }
    }

    // END OF BEDROCK CRACKING
    // START OF BEDROCK TNT PACKING

    // Stores number of TNT used on cracked bedrock.
    private val crackedBedrockTracker = BlockDataManager("cbdrk", PersistentDataType.INTEGER, events = false)

    @EventHandler(priority = EventPriority.HIGH)
    private fun PlayerInteractEvent.onPackTNTIntoCrackedBedrock() {
        if (useItemInHand() == Event.Result.DENY) return
        if (action != Action.RIGHT_CLICK_BLOCK) return
        if (player.isSneaking) return
        val block = clickedBlock ?: return
        if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.crackedBedrockId) {
            return
        }
        val item = item
        if (item == null || item.type != Material.TNT) {
            player.sendRichMessage(
                "<red>Use <item> to remove this bedrock.",
                Placeholder.component("item", ItemStack.of(Material.TNT).name())
            )
            return
        }
        isCancelled = true
        item.amount--
        val prevValue = crackedBedrockTracker[block] ?: 0
        val newValue = prevValue + 1
        if (newValue >= plugin.config.bedrockBreakTntAmount) {
            crackedBedrockTracker.remove(block)
            NexoBlocks.place(plugin.config.tntBedrockId, block.location)
        } else {
            crackedBedrockTracker[block] = newValue
        }
    }

    // END OF BEDROCK TNT PACKING
    // START OF TNT BEDROCK IGNITING

    @EventHandler(priority = EventPriority.HIGH)
    private fun PlayerInteractEvent.onTNTBedrockIgnite() {
        if (useItemInHand() == Event.Result.DENY) return
        if (action != Action.RIGHT_CLICK_BLOCK) return
        val block = clickedBlock ?: return
        if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.tntBedrockId) {
            return
        }
        val item = item ?: return
        if (item.type == Material.TNT && !player.isSneaking) {
            isCancelled = true
            return
        }
        if (item.type != Material.FLINT_AND_STEEL) return
        Task.syncDelayed { -> blowUpBedrock(block) }
    }

    @EventHandler
    private fun ProjectileHitEvent.onFireArrowLand() {
        val block = hitBlock ?: return
        if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.tntBedrockId) {
            return
        }
        if (entity.fireTicks <= 0) {
            return
        }
        Task.syncDelayed { -> blowUpBedrock(block) }
    }

    private fun blowUpBedrock(block: Block) {
        val onBottom = block.y == block.world.minHeight
        val newType = if (onBottom) Material.END_PORTAL else Material.AIR
        block.type = newType
        block.world.createExplosion(block.location.toCenterLocation(), 10f)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun BlockExplodeEvent.onBlockExplode() {
        for (block in blockList()) {
            if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.tntBedrockId) continue
            blowUpBedrock(block)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun EntityExplodeEvent.onEntityExplode() {
        for (block in blockList()) {
            if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.tntBedrockId) continue
            blowUpBedrock(block)
        }
    }

    // END OF TNT BEDROCK IGNITING
    // START OF PORTAL TELEPORTATION

    // Prevent multiple teleportations from touching multiple blocks
    private val alreadyTeleporting = ConcurrentSkipListSet<UUID>()

    @EventHandler
    private fun EntityInsideBlockEvent.onEndPortalTouch() {
        val block = this.block
        if (block.type != Material.END_PORTAL) return
        if (justTeleportedBack.contains(entity.uniqueId)) return
        if (!alreadyTeleporting.add(entity.uniqueId)) return
        isCancelled = true
        val inOverworld = plugin.config.netherWorldPairs.any { it.key == block.world.name }
        if (inOverworld) {
            portalToNether(entity, block)
        } else {
            portalToOverworld(entity, block)
        }
    }

    private fun findConnectedPortal(
        world: World,
        block: BlockPosition,
    ): Set<BlockPosition> {
        val portal = mutableSetOf<BlockPosition>()
        val visited = mutableSetOf<BlockPosition>()
        val queue = ArrayDeque<BlockPosition>()
        queue += block
        while (queue.isNotEmpty()) {
            val nextPos = queue.removeFirst()
            val added = visited.add(nextPos)
            if (!added) continue
            if (nextPos.toLocation(world).block.type != Material.END_PORTAL) continue
            portal += nextPos
            for (face in setOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
                queue += nextPos.offset(face)
            }
        }
        return portal
    }

    // The 3 blocks below the portal block may be bedrock.
    private fun clearTopBedrock(portalBlock: Block) {
        var block = portalBlock
        for (i in 1..4) {
            block = block.getRelative(BlockFace.DOWN)
            if (block.type == Material.BEDROCK) {
                block.type = Material.NETHERRACK
            }
        }
    }

    // Clears enough room for the entity,
    // but only removes non-player-placed blocks.
    private fun makeRoomFor(entity: Entity, portalBlock: Block) {
        val entityHeight = ceil(entity.height).toInt()
        var block = portalBlock
        for (i in 0..<entityHeight) {
            block = block.getRelative(BlockFace.DOWN)
            if (PlayerPlacedBlockTracker.isPlayerPlaced(block)) continue
            block.type = Material.AIR
        }
    }

    private fun portalToNether(entity: Entity, block: Block) {
        val worldPair = plugin.config.netherWorldPairs.entries.first { it.key == entity.world.name }
        val destWorld = Bukkit.getWorld(worldPair.value) ?: return // NOTTODO: not my problem
        val portalBlockPositions = findConnectedPortal(block.world, Position.block(block.location))
        val bottomBlocks = portalBlockPositions.map { it.toLocation(destWorld).block }
        val chunkKeysToLoad = bottomBlocks.map { it.x / 16 to it.z / 16 }.distinct()
        val chunkLoadFutures = chunkKeysToLoad.map { destWorld.getChunkAtAsync(it.first, it.second) }
        CompletableFuture.allOf(*chunkLoadFutures.toTypedArray())
            .thenCompose {
                val portalBlocks =
                    bottomBlocks.map { destWorld.getHighestBlockAt(it.x, it.z, HeightMap.WORLD_SURFACE) }
                portalBlocks.forEach {
                    it.type = Material.END_PORTAL
                    clearTopBedrock(it)
                    makeRoomFor(entity, it)
                }
                val height = ceil(entity.height).toInt()
                val destLocation = entity.location.clone()
                destLocation.world = destWorld
                destLocation.y = portalBlocks.first().y.toDouble() - height
                entity.fallDistance = 0f // because I'm so nice
                return@thenCompose entity.teleportAsync(destLocation).thenRun {
                    alreadyTeleporting.remove(entity.uniqueId)
                }
            }.handle { _, ex ->
                ex.printStackTrace()
            }
    }

    private val justTeleportedBack = mutableSetOf<UUID>()

    private fun portalToOverworld(entity: Entity, block: Block) {
        val worldPair = plugin.config.netherWorldPairs.entries.first { it.value == entity.world.name }
        val destWorld = Bukkit.getWorld(worldPair.key) ?: return
        val portalBlockPositions = findConnectedPortal(block.world, Position.block(block.location))
        val topBlocks = portalBlockPositions.map { it.toLocation(destWorld).block }
        val chunkKeysToLoad = topBlocks.map { it.x / 16 to it.z / 16 }.distinct()
        val chunkLoadFutures = chunkKeysToLoad.map { destWorld.getChunkAtAsync(it.first, it.second) }
        CompletableFuture.allOf(*chunkLoadFutures.toTypedArray())
            .thenCompose {
                // No clearing
                val destLocation = entity.location.clone()
                destLocation.world = destWorld
                destLocation.y = destWorld.minHeight + 1.0
                return@thenCompose entity.teleportAsync(
                    destLocation,
                    PlayerTeleportEvent.TeleportCause.PLUGIN,
                ).thenRun {
                    justTeleportedBack += entity.uniqueId
                    alreadyTeleporting.remove(entity.uniqueId)
                }
            }.handle { _, ex ->
                ex.printStackTrace()
            }
    }

    private fun moveAfterPortal(isHorizOrDown: () -> Boolean, entity: Entity): Boolean {
        val didJustTeleport = justTeleportedBack.contains(entity.uniqueId)
        if (!didJustTeleport) return false
        if (isHorizOrDown()) {
            // Moving down, prevent and set velocity again
            Task.syncDelayed { ->
                entity.velocity = entity.velocity.setY(1)
                (entity as? LivingEntity)?.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 2 * 20, 0))
            }
            return true
        } else {
            // Moving up
            justTeleportedBack.remove(entity.uniqueId)
            return false
        }
    }

    // Prevent player from falling until he has moved up.
    @EventHandler(ignoreCancelled = true)
    private fun PlayerMoveEvent.onMoveAfterPortal() {
        isCancelled = moveAfterPortal({ to.y <= from.y }, player)
    }

    // and any entity.
    @EventHandler(ignoreCancelled = true)
    private fun EntityMoveEvent.onMoveAfterPortal() {
        isCancelled = moveAfterPortal({ to.y <= from.y }, entity)
    }

    // END OF PORTAL TELEPORTATION
    // START OF VANILLA PORTAL DISABLE
    @EventHandler(ignoreCancelled = true)
    private fun PortalCreateEvent.onNetherPortalLight() {
        if (this.reason != PortalCreateEvent.CreateReason.FIRE) return
        isCancelled = true
        if (this.entity != null) {
            // TODO: journal entry
        }
    }
    // END OF VANILLA PORTAL DISABLE

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

}
