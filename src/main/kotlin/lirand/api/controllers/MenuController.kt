package lirand.api.controllers

import com.github.shynixn.mccoroutine.launch
import kotlinx.coroutines.delay
import lirand.api.dsl.menu.dynamic.anvil.AnvilMenu
import lirand.api.extensions.inventory.get
import lirand.api.extensions.inventory.isNotEmpty
import lirand.api.extensions.server.server
import lirand.api.menu.PlayerAnvilMenuPrepare
import lirand.api.menu.PlayerMenuComplete
import lirand.api.menu.PlayerMoveToMenu
import lirand.api.menu.StaticMenu
import lirand.api.menu.asMenu
import lirand.api.menu.getMenu
import lirand.api.menu.getSlotOrBaseSlot
import lirand.api.menu.slot.PlayerMenuSlotInteract
import lirand.api.menu.takeIfHasPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin

internal class MenuController(val plugin: Plugin) : Listener, Controller {

	private val supportedInventoryTypes = listOf(
		InventoryType.CHEST, InventoryType.ANVIL
	)

	@EventHandler
	fun onPluginDisableEvent(event: PluginDisableEvent) {
		for (player in server.onlinePlayers) {
			val holder = player.openInventory.topInventory.holder

			if (holder is StaticMenu<*>) {
				holder.close(player, true)
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	fun onClickEvent(event: InventoryClickEvent) {
		if (event.view.type !in supportedInventoryTypes)
			return

		val player = event.whoClicked as Player
		val inventory = event.inventory

		val menu = inventory.asMenu()?.takeIfHasPlayer(player) ?: return

		handleMenuClick(menu, event)
		handleMenuMove(menu, event)
	}

	private fun handleMenuClick(menu: StaticMenu<*>, event: InventoryClickEvent) {
		if (event.slot != event.rawSlot) return

		val slotIndex = event.slot + 1
		val slot = menu.getSlotOrBaseSlot(slotIndex)

		var interact = PlayerMenuSlotInteract(
			menu, event.inventory, event.whoClicked as Player,
			slotIndex, slot, slot.cancelEvents,
			event.click, event.action,
			event.currentItem, event.cursor,
			event.hotbarButton
		)

		if (event.slotType == InventoryType.SlotType.RESULT && event.currentItem.isNotEmpty) {
			val text = event.currentItem?.itemMeta?.displayName ?: ""
			interact = PlayerMenuComplete(interact, text)

			plugin.launch {
				delay(1)
				(menu as AnvilMenu).eventHandler.complete(interact)
			}
		}
		else {
			slot.eventHandler.interact(interact)
		}

		if (interact.canceled) event.isCancelled = true
	}

	private fun handleMenuMove(menu: StaticMenu<*>, event: InventoryClickEvent) {
		val slotIndex = event.rawSlot + 1
		val slot = menu.getSlotOrBaseSlot(slotIndex)

		with(event) {
			if ((this.slot == rawSlot && (cursor.isNotEmpty ||
						(hotbarButton != -1 && view.bottomInventory[hotbarButton].isNotEmpty)))
				|| (this.slot != rawSlot && this.click.isShiftClick && this.inventory.any { it.isNotEmpty })
			) {
				val movedItem = if (cursor.isNotEmpty)
					cursor
				else if (hotbarButton != -1)
					view.bottomInventory[hotbarButton]
				else
					currentItem

				val move = PlayerMoveToMenu(
					menu, whoClicked as Player, inventory,
					slot.cancelEvents, movedItem ?: return, hotbarButton
				)

				menu.eventHandler.moveToMenu(move)
			}
		}
	}

	@EventHandler
	fun onPrepareEvent(event: PrepareAnvilEvent) {
		val inventory = event.inventory
		val menu = inventory.asMenu() as? AnvilMenu ?: return

		val player = menu.viewers.firstNotNullOfOrNull { (player, menuInventory) ->
			if (inventory == menuInventory) player
			else null
		} ?: return

		val prepare = PlayerAnvilMenuPrepare(menu, player, inventory, event.result)

		plugin.launch {
			delay(1)
			menu.eventHandler.prepare(prepare)
		}
	}

	@EventHandler(ignoreCancelled = true)
	fun onDragEvent(event: InventoryDragEvent) {
		if (event.view.type !in supportedInventoryTypes)
			return

		val player = event.whoClicked as Player
		val menu = event.inventory.asMenu()?.takeIfHasPlayer(player)
		if (menu != null) {
			val pass = event.inventorySlots.firstOrNull { it in event.rawSlots }
			if (pass != null) event.isCancelled = true
		}
	}

	@EventHandler
	fun onCloseEvent(event: InventoryCloseEvent) {
		if (event.view.type !in supportedInventoryTypes)
			return

		val player = event.player as Player
		player.getMenu()?.close(player, false)
	}

	@EventHandler(ignoreCancelled = true)
	fun onPickupItemEvent(event: EntityPickupItemEvent) {
		val player = event.entity as? Player ?: return

		if (player.getMenu() != null)
			event.isCancelled = true
	}
}