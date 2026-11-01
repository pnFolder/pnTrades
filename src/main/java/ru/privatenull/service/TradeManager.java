package ru.privatenull.service;

import org.pnfolder.pnlibrary.api.tasks.TaskSpec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.PnTradesPlugin;
import ru.privatenull.config.GuiConfig;
import ru.privatenull.gui.TradeHolder;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TradeManager {
    static final int[] OFFER_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    static final int[] OTHER_SLOTS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    private final PnTradesPlugin plugin;
    private final GuiConfig gui;
    private final Map<UUID, TradeSession> sessionsByPlayer = new HashMap<>();
    private final Map<UUID, TradeSession> sessionsById = new HashMap<>();
    private final Map<UUID, TradeRequest> requestsByTarget = new HashMap<>();
    private final Set<Integer> offerSlots = new HashSet<>();

    public TradeManager(PnTradesPlugin plugin, GuiConfig gui) {
        this.plugin = plugin;
        this.gui = gui;
        for (int slot : OFFER_SLOTS) {
            offerSlots.add(slot);
        }
    }

    public void requestTrade(Player requester, Player target) {
        if (requester.equals(target)) {
            send(requester, "cannot-trade-self", "");
            return;
        }
        if (sessionsByPlayer.containsKey(requester.getUniqueId()) || sessionsByPlayer.containsKey(target.getUniqueId())) {
            send(requester, "already-trading", "");
            return;
        }
        requestsByTarget.put(target.getUniqueId(), new TradeRequest(requester.getUniqueId(), System.currentTimeMillis()));
        send(requester, "request-sent", target.getName());
        send(target, "request-received", requester.getName());
        sendRequestButtons(target, requester);
    }

    public void acceptRequest(Player target, Player requester) {
        TradeRequest request = requestsByTarget.get(target.getUniqueId());
        if (request == null || !request.requester().equals(requester.getUniqueId())) {
            send(target, "no-request", "");
            return;
        }
        requestsByTarget.remove(target.getUniqueId());
        if (System.currentTimeMillis() - request.createdAt() > requestTimeoutMillis()) {
            send(target, "request-expired", "");
            return;
        }
        if (sessionsByPlayer.containsKey(target.getUniqueId()) || sessionsByPlayer.containsKey(requester.getUniqueId())) {
            send(target, "already-trading", "");
            return;
        }

        TradeSession session = new TradeSession(requester.getUniqueId(), target.getUniqueId());
        session.firstInventory = createInventory(session, requester);
        session.secondInventory = createInventory(session, target);
        refresh(session);
        sessionsByPlayer.put(session.first, session);
        sessionsByPlayer.put(session.second, session);
        sessionsById.put(session.id, session);
        requester.openInventory(session.firstInventory);
        target.openInventory(session.secondInventory);
        send(requester, "trade-started", target.getName());
        send(target, "trade-started", requester.getName());
    }

    public void denyRequest(Player target, Player requester) {
        TradeRequest request = requestsByTarget.get(target.getUniqueId());
        if (request == null || !request.requester().equals(requester.getUniqueId())) {
            send(target, "no-request", "");
            return;
        }
        requestsByTarget.remove(target.getUniqueId());
        send(target, "request-denied", "");
        send(requester, "request-denied-by-player", target.getName());
    }

    public TradeSession getSession(TradeHolder holder, Player player) {
        TradeSession session = sessionsById.get(holder.sessionId());
        return session != null && holder.viewerId().equals(player.getUniqueId()) ? session : null;
    }

    public TradeSession getSessionFor(Player player) {
        return sessionsByPlayer.get(player.getUniqueId());
    }

    public boolean isOfferSlot(int slot) {
        return offerSlots.contains(slot);
    }

    public int ownConfirmSlot() {
        return gui.slot("buttons.self.no", 38);
    }

    public void offerChanged(TradeSession session) {
        session.firstConfirmed = false;
        session.secondConfirmed = false;
        refresh(session);
    }

    public void scheduleOfferChanged(TradeHolder holder, Player player, TradeSession session) {
        plugin.module().getTasks().schedule(TaskSpec.builder()
                .name("pntrades-offer-changed")
                .action(task -> {
                    if (getSession(holder, player) == session) {
                        offerChanged(session);
                    }
                })
                .build());
    }

    public void confirm(Player player, TradeSession session) {
        if (session.isFirst(player.getUniqueId())) {
            session.firstConfirmed = true;
        } else {
            session.secondConfirmed = true;
        }
        refresh(session);
        if (session.firstConfirmed && session.secondConfirmed) {
            complete(session);
        }
    }

    public void cancelFor(Player player, TradeSession session, String reason) {
        if (!sessionsById.containsKey(session.id)) {
            return;
        }
        removeSession(session);
        returnOffers(session, session.first, session.firstInventory);
        returnOffers(session, session.second, session.secondInventory);
        Player other = Bukkit.getPlayer(session.other(player.getUniqueId()));
        if (player.isOnline()) {
            send(player, "trade-cancelled", reason);
        }
        if (other != null && other.isOnline()) {
            send(other, "trade-cancelled", reason);
            other.closeInventory();
        }
    }

    public void closeAll() {
        for (TradeSession session : new ArrayList<>(sessionsById.values())) {
            removeSession(session);
            returnOffers(session, session.first, session.firstInventory);
            returnOffers(session, session.second, session.secondInventory);
        }
        requestsByTarget.clear();
    }

    private Inventory createInventory(TradeSession session, Player viewer) {
        Inventory inventory = Bukkit.createInventory(new TradeHolder(session.id, viewer.getUniqueId()), 54,
                gui.title(Bukkit.getOfflinePlayer(session.other(viewer.getUniqueId())).getName()));
        return inventory;
    }

    private void refresh(TradeSession session) {
        render(session, session.first, session.firstInventory);
        render(session, session.second, session.secondInventory);
    }

    private void render(TradeSession session, UUID viewerId, Inventory inventory) {
        boolean viewerIsFirst = session.isFirst(viewerId);
        Inventory otherInventory = viewerIsFirst ? session.secondInventory : session.firstInventory;
        boolean ownConfirmed = viewerIsFirst ? session.firstConfirmed : session.secondConfirmed;
        boolean otherConfirmed = viewerIsFirst ? session.secondConfirmed : session.firstConfirmed;

        for (int slot : OTHER_SLOTS) {
            inventory.setItem(slot, null);
        }
        for (int index = 0; index < OFFER_SLOTS.length; index++) {
            ItemStack offered = otherInventory.getItem(OFFER_SLOTS[index]);
            inventory.setItem(OTHER_SLOTS[index], offered == null ? null : offered.clone());
        }

        for (int slot : gui.slots("divider", List.of(4, 13, 22, 31, 40, 49))) {
            inventory.setItem(slot, gui.item("divider"));
        }
        int ownConfirmSlot = ownConfirmSlot();
        int otherConfirmSlot = gui.slot("buttons.other.no", 42);
        for (int slot = 36; slot < 54; slot++) {
            if (slot != ownConfirmSlot && slot != otherConfirmSlot && slot != 40 && slot != 49) {
                inventory.setItem(slot, gui.item("filler"));
            }
        }
        inventory.setItem(ownConfirmSlot, gui.item(ownConfirmed ? "buttons.self.yes" : "buttons.self.no"));
        inventory.setItem(otherConfirmSlot, gui.item(otherConfirmed ? "buttons.other.yes" : "buttons.other.no"));
    }

    private void complete(TradeSession session) {
        List<ItemStack> firstItems = takeOffers(session.firstInventory);
        List<ItemStack> secondItems = takeOffers(session.secondInventory);
        removeSession(session);
        Player first = Bukkit.getPlayer(session.first);
        Player second = Bukkit.getPlayer(session.second);
        if (first != null) {
            give(first, secondItems);
            first.closeInventory();
            send(first, "trade-completed", "");
        }
        if (second != null) {
            give(second, firstItems);
            second.closeInventory();
            send(second, "trade-completed", "");
        }
    }

    private void returnOffers(TradeSession session, UUID ownerId, Inventory inventory) {
        Player owner = Bukkit.getPlayer(ownerId);
        List<ItemStack> items = takeOffers(inventory);
        if (owner != null) {
            give(owner, items);
        }
    }

    private List<ItemStack> takeOffers(Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : OFFER_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
            inventory.setItem(slot, null);
        }
        return items;
    }

    private void give(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void removeSession(TradeSession session) {
        sessionsById.remove(session.id);
        sessionsByPlayer.remove(session.first);
        sessionsByPlayer.remove(session.second);
    }

    private long requestTimeoutMillis() {
        return Math.max(5, plugin.getConfig().getLong("request-timeout-seconds", 60)) * 1_000L;
    }

    private void send(Player player, String message, String name) {
        player.sendMessage(message(message, name));
    }

    public String message(String path, String playerName) {
        return plugin.message(path, playerName);
    }

    private void sendRequestButtons(Player target, Player requester) {
        TextComponent buttons = new TextComponent(plugin.message("request-buttons-prefix", requester.getName()));
        TextComponent accept = new TextComponent(plugin.message("accept-button", requester.getName()));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept " + requester.getName()));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent(plugin.message("accept-hover", requester.getName()))}));
        TextComponent deny = new TextComponent(plugin.message("deny-button", requester.getName()));
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny " + requester.getName()));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent(plugin.message("deny-hover", requester.getName()))}));
        buttons.addExtra(accept);
        buttons.addExtra(deny);
        target.spigot().sendMessage(buttons);
    }

    private record TradeRequest(UUID requester, long createdAt) {
    }
}
