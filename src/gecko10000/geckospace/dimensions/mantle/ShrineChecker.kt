package gecko10000.geckospace.dimensions.mantle

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import kotlin.math.max
import kotlin.random.Random

// Checks shrine given block where fire should be.
// checks for top layer:
// -r-
// rnr
// -r-
// bottom layer:
// ggg
// ggg
// ggg
// r: redstone torch, n: netherrack, g: gold block
class ShrineChecker(val fireBlock: Block, val lighter: Player) {

    val isValid: Boolean
    val world = fireBlock.world

    // [0, 4]
    val blocksToVoid
        get() = bedrockBlocksToCrack.first().y - world.minHeight

    companion object {
        private const val BEDROCK_LAYERS = 5
    }

    val closenessToVoid
        get() = max(1, BEDROCK_LAYERS - blocksToVoid)
    private val shrineBlocks = mutableListOf(fireBlock)
    fun getShrineBlocks(): List<Block> = shrineBlocks
    private val bedrockBlocksToCrack = mutableSetOf<Block>()
    fun getBedrockBlocksToCrack(): Set<Block> = bedrockBlocksToCrack

    fun getNearbyPlayers(radius: Double) = fireBlock.location.add(0.5, 0.0, 0.5).getNearbyPlayers(radius)

    fun findRandomTopBedrockAroundShrine(): Block {
        val isXFixed = Random.nextBoolean()
        val x = if (isXFixed) (if (Random.nextBoolean()) -2 else 2) else Random.nextInt(-2, 3)
        val z = if (isXFixed) Random.nextInt(-2, 3) else (if (Random.nextBoolean()) -2 else 2)
        val bottomBlock = world.getBlockAt(fireBlock.x + x, world.minHeight, fireBlock.z + z)
        var highestBedrock = bottomBlock
        var block = bottomBlock
        for (i in 1..<BEDROCK_LAYERS) {
            block = block.getRelative(BlockFace.UP)
            if (block.type == Material.BEDROCK) {
                highestBedrock = block
            }
        }
        return highestBedrock
    }

    private fun check(): Boolean {
        val netherrack = fireBlock.getRelative(BlockFace.DOWN)
        if (netherrack.type != Material.NETHERRACK) return false
        shrineBlocks += netherrack
        for (torchDirection in setOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            val redstoneTorch = netherrack.getRelative(torchDirection)
            // REDSTONE_WALL_TORCH is separate, no need to check BlockData
            if (redstoneTorch.type != Material.REDSTONE_TORCH) return false
            shrineBlocks += redstoneTorch
        }
        val goldCenter = netherrack.getRelative(BlockFace.DOWN)
        for (x in -1..1) {
            for (z in -1..1) {
                val goldBlock = goldCenter.getRelative(x, 0, z)
                if (goldBlock.type != Material.GOLD_BLOCK) return false
                shrineBlocks += goldBlock
                val bedrock = goldBlock.getRelative(BlockFace.DOWN)
                if (bedrock.type == Material.BEDROCK) {
                    bedrockBlocksToCrack += bedrock
                }
            }
        }
        return true
    }

    init {
        isValid = check()
    }

}
