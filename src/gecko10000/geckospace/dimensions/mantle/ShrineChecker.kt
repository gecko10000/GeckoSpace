package gecko10000.geckospace.dimensions.mantle

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace

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
class ShrineChecker(private val fireBlock: Block) {

    val isValid: Boolean
    private val shrineBlocks = mutableListOf(fireBlock)
    fun getShrineBlocks(): List<Block> = shrineBlocks
    private val bedrockBlocksToCrack = mutableSetOf<Block>()
    fun getBedrockBlocksToCrack(): Set<Block> = bedrockBlocksToCrack

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
        val bedrockBlocks = mutableSetOf<Block>()
        for (x in -1..1) {
            for (z in -1..<1) {
                val goldBlock = goldCenter.getRelative(x, 0, z)
                if (goldBlock.type != Material.GOLD_BLOCK) return false
                shrineBlocks += goldBlock
                val bedrock = goldBlock.getRelative(BlockFace.DOWN)
                if (bedrock.type == Material.BEDROCK) {
                    bedrockBlocks += bedrock
                }
            }
        }
        return true
    }

    init {
        isValid = check()
    }

}
