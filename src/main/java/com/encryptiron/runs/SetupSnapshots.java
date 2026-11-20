package com.encryptiron.runs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import javax.inject.Inject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

class SetupSnapshots
{
    private static final int INVENTORY_SIZE = 28;
    private static final int EQUIPMENT_SIZE = 14;
    private static final int[][] RUNE_POUCH_VARBITS = {
        {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1},
        {VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_QUANTITY_2},
        {VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_QUANTITY_3},
        {VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_QUANTITY_4},
    };

    @Inject
    private Client client;

    private final Deque<Snapshot> snapshots = new ArrayDeque<>();

    void record(long nowMs)
    {
        JsonObject setup = currentSetup();
        if (setup == null)
        {
            return;
        }

        String setupText = setup.toString();
        if (!snapshots.isEmpty() && snapshots.peekLast().setupText.equals(setupText))
        {
            return;
        }

        snapshots.addLast(new Snapshot(nowMs, setup, setupText));
    }

    JsonObject setupAt(long timeMs)
    {
        Snapshot chosen = null;
        for (Snapshot snapshot : snapshots)
        {
            if (snapshot.timeMs > timeMs)
            {
                return chosen != null ? chosen.setup : snapshot.setup;
            }
            chosen = snapshot;
        }

        return chosen == null ? null : chosen.setup;
    }

    void dropOlderThan(long cutoffMs)
    {
        while (snapshots.size() > 1 && secondSnapshot().timeMs <= cutoffMs)
        {
            snapshots.removeFirst();
        }
    }

    private Snapshot secondSnapshot()
    {
        Iterator<Snapshot> iterator = snapshots.iterator();
        iterator.next();
        return iterator.next();
    }

    private JsonObject currentSetup()
    {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        if (inventory == null && equipment == null)
        {
            return null;
        }

        JsonObject setup = new JsonObject();
        setup.add("inv", itemsOf(inventory, INVENTORY_SIZE));
        setup.add("eq", itemsOf(equipment, EQUIPMENT_SIZE));
        setup.add("rp", runePouch());
        setup.addProperty("sb", client.getVarbitValue(VarbitID.SPELLBOOK));

        int quiverAmmo = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
        int quiverAmount = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
        if (quiverAmmo > 0 && quiverAmount > 0)
        {
            JsonArray quiver = new JsonArray();
            quiver.add(item(quiverAmmo, quiverAmount));
            setup.add("qv", quiver);
        }

        return setup;
    }

    private JsonArray itemsOf(ItemContainer container, int size)
    {
        JsonArray items = new JsonArray();
        Item[] containerItems = container == null ? new Item[0] : container.getItems();
        for (int slot = 0; slot < size; slot++)
        {
            Item slotItem = slot < containerItems.length ? containerItems[slot] : null;
            items.add(slotItem == null || slotItem.getId() <= 0
                ? JsonNull.INSTANCE
                : item(slotItem.getId(), slotItem.getQuantity()));
        }

        return items;
    }

    private JsonArray runePouch()
    {
        EnumComposition runeTypes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
        JsonArray runes = new JsonArray();
        for (int[] varbits : RUNE_POUCH_VARBITS)
        {
            int runeType = client.getVarbitValue(varbits[0]);
            int quantity = client.getVarbitValue(varbits[1]);
            int itemId = runeType > 0 && runeTypes != null ? runeTypes.getIntValue(runeType) : -1;
            runes.add(itemId > 0 && quantity > 0 ? item(itemId, quantity) : JsonNull.INSTANCE);
        }

        return runes;
    }

    private static JsonElement item(int id, int quantity)
    {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("q", quantity);
        return item;
    }

    private static class Snapshot
    {
        private final long timeMs;
        private final JsonObject setup;
        private final String setupText;

        private Snapshot(long timeMs, JsonObject setup, String setupText)
        {
            this.timeMs = timeMs;
            this.setup = setup;
            this.setupText = setupText;
        }
    }
}
