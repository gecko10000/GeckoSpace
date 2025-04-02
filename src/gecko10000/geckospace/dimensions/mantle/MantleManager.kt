package gecko10000.geckospace.dimensions.mantle

import com.nexomc.nexo.api.NexoBlocks
import gecko10000.geckolib.blockdata.BlockDataManager
import gecko10000.geckolib.extensions.name
import gecko10000.geckospace.GeckoSpace
import gecko10000.geckospace.di.MyKoinComponent
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.trait.SkinTrait
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.EntityType
import org.bukkit.entity.TextDisplay
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
import org.koin.core.component.inject
import redempt.redlib.misc.Task
import java.util.*

// Mantle is just the nether,
// with custom entry/exit mechanics.
class MantleManager : MyKoinComponent, Listener {

    private val plugin: GeckoSpace by inject()

    // Stores text display UUIDs for updating.
    private val portalTracker = BlockDataManager("nptl", PersistentDataType.STRING, events = false)

    // Plays cave noises for first five seconds
    // Also spawns the entity to prepare it
    private fun herobrine1(shrineChecker: ShrineChecker) {
        // TODO: make the blocks temporarily immovable/unbreakable
        val nearbyPlayers = shrineChecker.fireBlock.location.add(0.5, 0.0, 0.5).getNearbyPlayers(25.0)
        for (player in nearbyPlayers) {
            for (i in 1..5) {
                player.playSound(player, Sound.AMBIENT_CAVE, 1f, 0.5f)
            }
        }

        val npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Herobrine")
        val spawnLocation = shrineChecker.fireBlock.location
        spawnLocation.y = shrineChecker.world.minHeight - 100.0 // Ensure it's not visible yet
        npc.spawn(spawnLocation)
        val skinTrait = npc.getOrAddTrait(SkinTrait::class.java)
        skinTrait.setSkinPersistent("herobrine", plugin.config.herobrineSkinSignature, plugin.config.herobrineSkinValue)
        npc.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, "false")

        Task.syncDelayed({ ->
            herobrine2(npc, shrineChecker)
        }, 100L)
    }

    // Strikes lightning and replaces blocks with nether counterparts
    private fun herobrine2(npc: NPC, shrineChecker: ShrineChecker) {
        val blocksToReplace = shrineChecker.getShrineBlocks()
        blocksToReplace.filter { it.type == Material.REDSTONE_TORCH }.forEach {
            it.world.strikeLightning(it.location.add(0.5, 0.0, 0.5))
        }
        for (block in blocksToReplace) {
            val newType = when (block.type) {
                Material.GOLD_BLOCK -> Material.COAL_BLOCK
                Material.REDSTONE_TORCH -> Material.SOUL_TORCH
                Material.NETHERRACK -> Material.SOUL_SOIL
                else -> block.type
            }
            block.type = newType
        }
        shrineChecker.fireBlock.type = Material.SOUL_FIRE
        Task.syncDelayed({ -> herobrine3(npc, shrineChecker) }, 20L)
    }

    // Schedule shrine block breaking and bedrock cracking
    private fun herobrine3(npc: NPC, shrineChecker: ShrineChecker) {
        val blocks = shrineChecker.getShrineBlocks()
        for (i in blocks.indices) {
            val block = blocks[i]
            Task.syncDelayed({ ->
                npc.teleport(block.location.add(0.5, 0.0, 0.5), PlayerTeleportEvent.TeleportCause.PLUGIN)
                // TODO: make him break the block
            }, 5L * i)
        }
    }

    private fun crackBedrock(blocks: Set<Block>) {
        blocks.forEach { block ->
            NexoBlocks.place(plugin.config.crackedBedrockId, block.location)
            block.world.strikeLightningEffect(block.location.add(0.5, 0.0, 0.5))
        }
    }

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
        portalTracker[block] = display.uniqueId.toString()
    }

    private fun placePortal(block: Block) {
        block.type = Material.END_PORTAL
        updatePortalTextDisplay(block)
        block.world.playSound(
            block.location.add(0.5, 0.0, 0.5),
            Sound.BLOCK_PORTAL_TRIGGER,
            SoundCategory.BLOCKS,
            1f,
            1f
        )
    }

    private fun breakCrackedBedrock(block: Block) {
        val onBottom = block.y == block.world.minHeight
        if (!onBottom) {
            block.type = Material.AIR
        } else {
            placePortal(block)
        }
    }

    @EventHandler
    private fun EntitiesLoadEvent.onPortalTextDisplayLoad() {
        for (block in portalTracker.getValuedBlocks(chunk)) {
            updatePortalTextDisplay(block)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private fun PlayerInteractEvent.onIgniteShrine() {
        if (useItemInHand() == Event.Result.DENY) return
        if (action != Action.RIGHT_CLICK_BLOCK) return
        val block = clickedBlock
        if (block == null || block.type != Material.NETHERRACK) return
        if (blockFace != BlockFace.UP) return
        if (item?.type != Material.FLINT_AND_STEEL) return
        val shrineChecker = ShrineChecker(block.getRelative(BlockFace.UP))
        if (!shrineChecker.isValid) return
        if (shrineChecker.getBedrockBlocksToCrack().isEmpty()) {
            player.sendRichMessage("<red>This only works over bedrock...")
            return
        }
        herobrine1(shrineChecker)
    }

    @EventHandler(priority = EventPriority.HIGH)
    private fun PlayerInteractEvent.onPackTNTIntoCrackedBedrock() {
        if (useItemInHand() == Event.Result.DENY) return
        if (action != Action.RIGHT_CLICK_BLOCK) return
        val block = clickedBlock ?: return
        if (NexoBlocks.noteBlockMechanic(block)?.itemID != plugin.config.crackedBedrockId) {
            return
        }
        isCancelled = true
        val item = item
        if (item == null || item.type != Material.TNT || item.amount < plugin.config.bedrockBreakTntAmount) {
            player.sendRichMessage(
                "<red>Use <amount> <item> to remove this bedrock.",
                Placeholder.unparsed("amount", plugin.config.bedrockBreakTntAmount.toString()),
                Placeholder.component("item", ItemStack.of(Material.TNT).name())
            )
            return
        }
        item.amount -= plugin.config.bedrockBreakTntAmount
        Task.syncDelayed { -> breakCrackedBedrock(block) }
    }

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

}
