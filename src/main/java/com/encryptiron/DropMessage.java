package com.encryptiron;

import java.util.Collection;

import net.runelite.client.game.ItemStack;

public class DropMessage implements IMessage {

    private long accountHash;
    private String clanUsername;
    private String clanEventKeyword;
    private Collection<ItemStack> items;

    DropMessage(long accountHash, String clanUsername, String clanEventKeyword, Collection<ItemStack> itemsObtained)
    {
        this.accountHash = accountHash;
        this.clanUsername = clanUsername;
        this.clanEventKeyword = clanEventKeyword;
        this.items = itemsObtained;
    }

    private void writeMessage(StringBuilder builder, String fieldName, String fieldValue)
    {
        builder.append("[" + fieldName + "]" + fieldValue + "\n");
    }

    @Override
    public void write(StringBuilder builder) {
        writeMessage(builder, "ACCOUNT_HASH", String.valueOf(accountHash));
        writeMessage(builder, "USERNAME", clanUsername);
        writeMessage(builder, "KEYWORD", clanEventKeyword);

        int counter = 1;
        for (ItemStack item : items)
        {
            writeMessage(builder, "ITEM" + String.valueOf(counter++), String.valueOf(item.getId()));
        }
    }
}
