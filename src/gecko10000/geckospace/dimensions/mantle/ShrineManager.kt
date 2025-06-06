package gecko10000.geckospace.dimensions.mantle

import com.nexomc.nexo.api.NexoBlocks
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import io.papermc.paper.entity.LookAnchor
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.trait.SkinTrait
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.EntityType
import org.bukkit.entity.Ghast
import org.bukkit.entity.MagmaCube
import org.bukkit.entity.Mob
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.koin.core.component.inject
import redempt.redlib.misc.Task
import kotlin.random.Random

// Manages Herobrine shrines and
// bedrock cracking
class ShrineManager : MyKoinComponent, Listener {

    private val plugin: GeckoSpace by inject()

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

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

}
