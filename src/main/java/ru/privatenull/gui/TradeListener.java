package ru.privatenull.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.privatenull.service.TradeManager;
import ru.privatenull.service.TradeSession;

public final class TradeListener implements Listener {
    private final TradeManager manager;

    public TradeListener(TradeManager manager) {
        this.manager = manager;
    }

    @EventHandler
    void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TradeHolder holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        TradeSession session = manager.getSession(holder, player);
        if (session == null) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return;
        }
        if (rawSlot >= top.getSize()) {
            // Player-inventory quick moves and collect-to-cursor must not alter offers invisibly.
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot == manager.ownConfirmSlot()) {
            event.setCancelled(true);
            manager.confirm(player, session);
            return;
        }
        if (!manager.isOfferSlot(rawSlot)) {
            event.setCancelled(true);
            return;
        }
        // The inventory update happens after this event, so render on the next tick.
        manager.scheduleOfferChanged(holder, player, session);
    }

    @EventHandler
    void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TradeHolder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeHolder holder) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        TradeSession session = manager.getSession(holder, player);
        if (session != null) {
            manager.cancelFor(player, session, "окно было закрыто");
        }
    }

    @EventHandler
    void onPlayerQuit(PlayerQuitEvent event) {
        TradeSession session = manager.getSessionFor(event.getPlayer());
        if (session != null) {
            manager.cancelFor(event.getPlayer(), session, "игрок вышел с сервера");
        }
    }
}
