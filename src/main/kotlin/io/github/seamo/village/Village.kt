package io.github.seamo.village

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.math.pow

class Village : JavaPlugin(), Listener {
    private val chunkOwners = mutableMapOf<UUID, MutableSet<Pair<Int, Int>>>()
    private val maxChunks = mutableMapOf<UUID, Int>()

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        // 좌클릭 && 쉬프트가 눌렸을 때만 GUI 열기
        if (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK) {
            if (player.isSneaking) {
                openVillageGUI(player)
            }
        }
    }

    private fun openVillageGUI(player: Player) {
        val gui = Bukkit.createInventory(null, 9, "§2§lVillage GUI")
        val claimLand = ItemStack(Material.GRASS_BLOCK).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§2§l땅 늘리기") }
        }
        val increaseMaxChunks = ItemStack(Material.TOTEM_OF_UNDYING).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§2§l최대 보유 갯수 증가") }
        }
        val removeLand = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§4§l땅 삭제") }
        }

        gui.setItem(0, claimLand)
        gui.setItem(1, increaseMaxChunks)
        gui.setItem(2, removeLand)  // 3번째 칸에 "땅 삭제" 추가

        player.openInventory(gui)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.view.title != "§2§lVillage GUI" || event.clickedInventory?.type == InventoryType.PLAYER) return
        event.isCancelled = true

        when (event.slot) {
            0 -> claimChunk(player)
            1 -> increaseMaxChunks(player)
            2 -> removeChunk(player)  // "땅 삭제" 클릭 시 처리
        }
    }

    private fun claimChunk(player: Player) {
        val loc = player.location
        val chunk = loc.chunk
        val playerUUID = player.uniqueId

        if (chunkOwners.any { it.value.contains(chunk.x to chunk.z) }) {
            player.sendMessage("§c§l이미 소유된 땅입니다!")
            return
        }

        val ownedChunks = chunkOwners.getOrPut(playerUUID) { mutableSetOf() }
        val maxAllowed = maxChunks.getOrDefault(playerUUID, 10)

        if (ownedChunks.size >= maxAllowed) {
            player.sendMessage("§c§l최대 소유 가능 청크 수를 초과했습니다!")
            return
        }

        val cost = (2.0.pow(ownedChunks.size)).toInt().coerceAtMost(2048)
        if (!player.inventory.containsAtLeast(ItemStack(Material.EMERALD), cost)) {
            player.sendMessage("§c§l에메랄드가 부족합니다! §2§l현재 필요한 에메랄드 갯수: $cost")
            return
        }

        player.inventory.removeItem(ItemStack(Material.EMERALD, cost))
        ownedChunks.add(chunk.x to chunk.z)
        player.sendMessage("§a§l청크를 얻었습니다! (${ownedChunks.size}/$maxAllowed) §2§l+사용 에메랄드 갯수: $cost")
    }

    private fun increaseMaxChunks(player: Player) {
        val playerUUID = player.uniqueId
        if (!player.inventory.containsAtLeast(ItemStack(Material.TOTEM_OF_UNDYING), 1)) {
            player.sendMessage("§c§l불사의 토템이 부족합니다!")
            return
        }
        player.inventory.removeItem(ItemStack(Material.TOTEM_OF_UNDYING, 1))
        maxChunks[playerUUID] = maxChunks.getOrDefault(playerUUID, 10) + 1
        player.sendMessage("§2§l최대 청크 개수가 증가했습니다! (${maxChunks[playerUUID]})")
    }
    private fun removeChunk(player: Player) {
        val playerUUID = player.uniqueId
        val loc = player.location
        val chunk = loc.chunk

        if (chunkOwners[playerUUID]?.contains(chunk.x to chunk.z) == true) {
            chunkOwners[playerUUID]?.remove(chunk.x to chunk.z)
            player.sendMessage("§a§l청크가 삭제되었습니다!")
        } else {
            player.sendMessage("§c§l자신의 청크가 아닙니다!")
        }
    }
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val chunk = player.location.chunk
        val playerUUID = player.uniqueId

        when {
            chunkOwners[playerUUID]?.contains(chunk.x to chunk.z) == true ->
                player.sendActionBar("§a§l자신의 청크입니다")
            chunkOwners.any { it.value.contains(chunk.x to chunk.z) } ->
                player.sendActionBar("§c§l다른 사람의 청크입니다")
            else -> player.sendActionBar("§7§l빈 청크입니다")
        }
    }

    @EventHandler
    fun onPlayer(event: PlayerInteractEvent) {
        val player = event.player
        val loc = player.location
        val chunk = loc.chunk
        val playerUUID = player.uniqueId

        // 빈 청크이거나 다른 사람의 청크에서 상호작용 불가
        if (chunkOwners.none { it.value.contains(chunk.x to chunk.z) } || chunkOwners.any { it.value.contains(chunk.x to chunk.z) && it.key != playerUUID }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val loc = event.block.location
        val chunk = loc.chunk
        val playerUUID = player.uniqueId

        // 빈 청크이거나 다른 사람의 청크에서 블록 파괴 불가
        if (chunkOwners.none { it.value.contains(chunk.x to chunk.z) } || chunkOwners.any { it.value.contains(chunk.x to chunk.z) && it.key != playerUUID }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val loc = event.block.location
        val chunk = loc.chunk
        val playerUUID = player.uniqueId

        // 빈 청크이거나 다른 사람의 청크에서 블록 설치 불가
        if (chunkOwners.none { it.value.contains(chunk.x to chunk.z) } || chunkOwners.any { it.value.contains(chunk.x to chunk.z) && it.key != playerUUID }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        // 폭발로 인한 블록 파괴 방지
        event.blockList().clear()
    }
}
