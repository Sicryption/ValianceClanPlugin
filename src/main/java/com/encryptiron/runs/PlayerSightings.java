package com.encryptiron.runs;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class PlayerSightings
{
    private final TimeSpans present = new TimeSpans();
    private final TimeSpans inParty = new TimeSpans();
    private final Map<String, TimeSpans> targetedByNpcName = new HashMap<>();

    void seenPresent(long timeMs)
    {
        present.add(timeMs);
    }

    void seenInParty(long timeMs)
    {
        inParty.add(timeMs);
    }

    void seenTargeting(String npcName, long timeMs)
    {
        targetedByNpcName.computeIfAbsent(npcName, name -> new TimeSpans()).add(timeMs);
    }

    void dropEndedBefore(long timeMs)
    {
        present.dropEndedBefore(timeMs);
        inParty.dropEndedBefore(timeMs);
        targetedByNpcName.values().forEach(spans -> spans.dropEndedBefore(timeMs));
        targetedByNpcName.values().removeIf(TimeSpans::isEmpty);
    }

    boolean isEmpty()
    {
        return present.isEmpty() && inParty.isEmpty() && targetedByNpcName.isEmpty();
    }

    JsonObject toJsonWithin(String playerName, long fromMs, long toMs)
    {
        JsonArray presentOffsets = present.toOffsetsWithin(fromMs, toMs);
        JsonArray partyOffsets = inParty.toOffsetsWithin(fromMs, toMs);

        JsonObject targets = new JsonObject();
        targetedByNpcName.forEach((npcName, spans) -> {
            JsonArray targetOffsets = spans.toOffsetsWithin(fromMs, toMs);
            if (targetOffsets.size() > 0)
            {
                targets.add(npcName, targetOffsets);
            }
        });

        if (presentOffsets.size() == 0 && partyOffsets.size() == 0 && targets.size() == 0)
        {
            return null;
        }

        JsonObject json = new JsonObject();
        json.addProperty("name", playerName);
        json.add("present", presentOffsets);
        json.add("party", partyOffsets);
        json.add("targets", targets);
        return json;
    }
}
