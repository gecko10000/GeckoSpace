package gecko10000.geckospace.dimensions.mantle

import com.nexomc.nexo.api.NexoBlocks
import gecko10000.geckolib.blockdata.BlockDataManager
import gecko10000.geckolib.extensions.name
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import io.papermc.paper.entity.LookAnchor
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.trait.SkinTrait
import net.kyori.adventure.text.Component
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
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.joml.Matrix4f
import org.koin.core.component.inject
import redempt.redlib.misc.Task
import java.util.*
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
                val spawnLocation = shrineChecker.findRandomTopBedrockAroundShrine()
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
        block.world.createExplosion(block.location.toCenterLocation(), 10f)
        Task.syncDelayed { -> blowUpBedrock(block) }
    }

    private fun blowUpBedrock(block: Block) {
        val onBottom = block.y == block.world.minHeight
        if (!onBottom) {
            block.type = Material.AIR
        } else {
            placePortal(block)
        }
    }

    private fun placePortal(block: Block) {
        block.type = Material.END_PORTAL
        updatePortalTextDisplay(block)
//        block.world.playSound(
//            block.location.add(0.5, 0.0, 0.5),
//            Sound.BLOCK_PORTAL_TRIGGER,
//            SoundCategory.BLOCKS,
//            1f,
//            1f
//        )
    }

    // END OF TNT BEDROCK IGNITING
    // START OF COLOR FILTER TEXT DISPLAYS

    // Stores text display UUIDs for updating.
    private val portalTracker = BlockDataManager("nptl", PersistentDataType.STRING, events = false)

    private fun updatePortalTextDisplay(block: Block) {
        val textDisplayUUID = portalTracker[block]
        val existingEntity = textDisplayUUID?.let { Bukkit.getEntity(UUID.fromString(it)) } as? TextDisplay
        if (block.type != Material.END_PORTAL) {
            plugin.logger.warning("End portal at ${block.location} missing, removing its text display.")
            portalTracker.remove(block)
            existingEntity?.remove()
            return
        }
        val display = existingEntity ?: block.world.spawn(block.location, TextDisplay::class.java)
        display.backgroundColor = Color.fromRGB(0x89008b)
        display.textOpacity = (0.4 * 256).toInt().toByte()
        display.text(Component.text(" "))
        // https://github.com/TheCymaera/minecraft-text-display-experiments/blob/main/src/main/java/com/heledron/text_display_experiments/TextDisplayExperimentsPlugin.kt#L12
        display.setTransformationMatrix(
            Matrix4f()
                .translate(-0.1f + .5f, -0.5f + .5f, 0f)
                .scale(8.0f, 4.0f, 1f)
        )
        portalTracker[block] = display.uniqueId.toString()
    }

    private fun updateDisplaysInChunk(chunk: Chunk) {
        for (block in portalTracker.getValuedBlocks(chunk)) {
            updatePortalTextDisplay(block)
        }
    }

    @EventHandler
    private fun EntitiesLoadEvent.onPortalTextDisplayLoad() {
        updateDisplaysInChunk(chunk)
    }

    init {
        Bukkit.getWorlds()
            .flatMap { it.loadedChunks.toList() }
            .forEach(::updateDisplaysInChunk)
        // END OF COLOR FILTER TEXT DISPLAYS

        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

}
