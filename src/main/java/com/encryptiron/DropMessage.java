package com.encryptiron;

import net.runelite.client.game.ItemStack;

import java.util.Collection;

public class DropMessage implements IMessage {

    private long accountHash;
    private String clanUsername;
    private Collection<ItemStack> items;

    DropMessage(long accountHash, String clanUsername, Collection<ItemStack> itemsObtained)
    {
        this.accountHash = accountHash;
        this.clanUsername = clanUsername;
        this.items = itemsObtained;
    }

    private void writeMessage(StringBuilder builder, String fieldName, String fieldValue)
    {
        builder.append("[" + fieldName + "]" + fieldValue);
    }

    @Override
    public void write(StringBuilder builder) {
        writeMessage(builder, "ACCOUNT_HASH", String.valueOf(accountHash));
        writeMessage(builder, "USERNAME", clanUsername);

        int counter = 1;
        for (ItemStack item : items)
        {
            writeMessage(builder, "ITEM" + String.valueOf(counter++), String.valueOf(item.getId()));
        }
    }
}
