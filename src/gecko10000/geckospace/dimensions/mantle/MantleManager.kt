package gecko10000.geckospace.dimensions.mantle

import com.nexomc.nexo.api.NexoBlocks
import gecko10000.geckolib.blockdata.BlockDataManager
import gecko10000.geckolib.extensions.name
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import io.papermc.paper.entity.LookAnchor
import io.papermc.paper.event.entity.EntityInsideBlockEvent
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.trait.SkinTrait
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
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
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.inject
import redempt.redlib.misc.Task
import kotlin.math.ceil
import kotlin.random.Random

// Mantle is just the nether,
// with custom entry/exit mechanics.
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
        val isValidWorld = plugin.config.netherWorldPairs.any { it.first == block.world.name }
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

    // END OF TNT BEDROCK IGNITING
    // START OF PORTAL TELEPORTATION

    @EventHandler
    private fun EntityInsideBlockEvent.onEndPortalTouch() {
        val block = this.block
        if (block.type != Material.END_PORTAL) return
        isCancelled = true
        val inOverworld = plugin.config.netherWorldPairs.any { it.first == block.world.name }
        if (inOverworld) {
            portalToNether(entity)
        } else {
            portalToOverworld(entity)
        }
    }

    private fun portalToNether(entity: Entity) {
        val worldPair = plugin.config.netherWorldPairs.first { it.first == entity.world.name }
        val destWorld = Bukkit.getWorld(worldPair.second) ?: return // TODO: uhhh?
        val sourceBlock = entity.location.block
        destWorld.getChunkAtAsync(sourceBlock.x / 16, sourceBlock.z / 16)
            .thenApplyAsync({ chunk ->
                val topBedrock = destWorld.getHighestBlockAt(sourceBlock.x, sourceBlock.z)
                val portalLocation = topBedrock.getRelative(BlockFace.DOWN)
                portalLocation.type = Material.END_PORTAL
                val entityHeight = ceil(entity.height).toInt()
                var block = portalLocation
                // TODO: only clear player-placed blocks
                // TODO: clear bedrock
                // TODO: clear entire connected portal?
                for (i in 0..<entityHeight) {
                    block = block.getRelative(BlockFace.DOWN)
                    block.type = Material.AIR
                }
            }, Bukkit.getScheduler().getMainThreadExecutor(plugin))
    }

    private fun portalToOverworld(entity: Entity) {

    }

    // END OF PORTAL TELEPORTATION

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

}
