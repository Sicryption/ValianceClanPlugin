package com.encryptiron.rest;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class OnBossKilled extends PostCommand
{
    private static final Map<Integer, String> varpIdToBossName;

    static 
    {
        Map<Integer, String> tempMap = new HashMap<>();
        
        tempMap.put(VarPlayerID.TOTAL_ABYSSALSIRE_KILLS, "Abyssal Sire");
        tempMap.put(VarPlayerID.TOTAL_HYDRABOSS_KILLS, "Alchemical Hydra");
        tempMap.put(VarPlayerID.TOTAL_AMOXLIATL_KILLS, "Amoxliatl");
        tempMap.put(VarPlayerID.TOTAL_ARAXXOR_KILLS, "Araxxor");
        tempMap.put(VarPlayerID.TOTAL_ARTIO_KILLS, "Artio");
        tempMap.put(VarPlayerID.TOTAL_BARROWS_CHESTS, "Barrows Chests");
        tempMap.put(VarPlayerID.TOTAL_COWBOSS_KILLS, "Brutus");
        tempMap.put(VarPlayerID.TOTAL_BRYOPHYTA_KILLS, "Bryophyta");
        tempMap.put(VarPlayerID.TOTAL_CALLISTO_KILLS, "Callisto");
        tempMap.put(VarPlayerID.TOTAL_CALVARION_KILLS, "Calvar'ion");
        tempMap.put(VarPlayerID.TOTAL_CERBERUS_KILLS, "Cerberus");
        tempMap.put(VarPlayerID.TOTAL_COMPLETED_XERICCHAMBERS, "Chambers of Xeric");
        tempMap.put(VarPlayerID.TOTAL_COMPLETED_XERICCHAMBERS_CHALLENGE, "Chambers of Xeric: Challenge Mode");
        tempMap.put(VarPlayerID.TOTAL_CHAOSELE_KILLS, "Chaos Elemental");
        tempMap.put(VarPlayerID.TOTAL_CHAOSFANATIC_KILLS, "Chaos Fanatic");
        tempMap.put(VarPlayerID.TOTAL_SARADOMIN_KILLS, "Commander Zilyana");
        tempMap.put(VarPlayerID.TOTAL_CORP_KILLS, "Corporeal Beast");
        tempMap.put(VarPlayerID.TOTAL_CRAZYARCHAEOLOGIST_KILLS, "Crazy Archaeologist");
        tempMap.put(VarPlayerID.TOTAL_PRIME_KILLS, "Dagannoth Prime");
        tempMap.put(VarPlayerID.TOTAL_REX_KILLS, "Dagannoth Rex");
        tempMap.put(VarPlayerID.TOTAL_SUPREME_KILLS, "Dagannoth Supreme");
        tempMap.put(VarPlayerID.TOTAL_DERANGEDARCHAEOLOGIST_KILLS, "Deranged Archaeologist");
        tempMap.put(VarPlayerID.TOTAL_DUKE_SUCELLUS_KILLS, "Duke Sucellus");
        tempMap.put(VarPlayerID.TOTAL_BANDOS_KILLS, "General Graardor");
        tempMap.put(VarPlayerID.TOTAL_MOLE_KILLS, "Giant Mole");
        tempMap.put(VarPlayerID.TOTAL_GARGBOSS_KILLS, "Grotesque Guardians");
        tempMap.put(VarPlayerID.TOTAL_HESPORI_KILLS, "Hespori");
        tempMap.put(VarPlayerID.TOTAL_KALPHITE_KILLS, "Kalphite Queen");
        tempMap.put(VarPlayerID.TOTAL_KBD_KILLS, "King Black Dragon");
        tempMap.put(VarPlayerID.TOTAL_KRAKEN_BOSS_KILLS, "Kraken");
        tempMap.put(VarPlayerID.TOTAL_ARMADYL_KILLS, "Kree'Arra");
        tempMap.put(VarPlayerID.TOTAL_ZAMORAK_KILLS, "K'ril Tsutsaroth");
        tempMap.put(VarPlayerID.TOTAL_PMOON_CHESTS, "Lunar Chests");
        tempMap.put(VarPlayerID.TOTAL_MIMIC_KILLS, "Mimic");
        tempMap.put(VarPlayerID.TOTAL_NEX_KILLS, "Nex");
        tempMap.put(VarPlayerID.TOTAL_NIGHTMARE_KILLS, "Nightmare");
        tempMap.put(VarPlayerID.TOTAL_NIGHTMARE_CHALLENGE_KILLS, "Phosani's Nightmare");
        tempMap.put(VarPlayerID.TOTAL_HILLGIANT_BOSS_KILLS, "Obor");
        tempMap.put(VarPlayerID.TOTAL_MUSPAH_KILLS, "Phantom Muspah");
        tempMap.put(VarPlayerID.TOTAL_SARACHNIS_KILLS, "Sarachnis");
        tempMap.put(VarPlayerID.TOTAL_SCORPIA_KILLS, "Scorpia");
        tempMap.put(VarPlayerID.TOTAL_RAT_BOSS_KILLS, "Scurrius");
        tempMap.put(VarPlayerID.TOTAL_GRYPHON_BOSS_KILLS, "Shellbane Gryphon");
        tempMap.put(VarPlayerID.TOTAL_CATA_BOSS_KILLS, "Skotizo");
        tempMap.put(VarPlayerID.TOTAL_SOL_KILLS, "Sol Heredit");
        tempMap.put(VarPlayerID.TOTAL_SPINDEL_KILLS, "Spindel");
        tempMap.put(VarPlayerID.TOTAL_TEMPOROSS_KILLS, "Tempoross");
        tempMap.put(VarPlayerID.TOTAL_COMPLETED_GAUNTLET, "The Gauntlet");
        tempMap.put(VarPlayerID.TOTAL_COMPLETED_GAUNTLET_HM, "The Corrupted Gauntlet");
        tempMap.put(VarPlayerID.TOTAL_HUEY_KILLS, "The Hueycoatl");
        tempMap.put(VarPlayerID.TOTAL_LEVIATHAN_KILLS, "The Leviathan");
        tempMap.put(VarPlayerID.TOTAL_ROYAL_TITAN_KILLS, "The Royal Titans");
        tempMap.put(VarPlayerID.TOTAL_WHISPERER_KILLS, "The Whisperer");
        tempMap.put(VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD, "Theatre of Blood");
        tempMap.put(VarPlayerID.TOTAL_COMPLETED_THEATREOFBLOOD_HARD, "Theatre of Blood: Hard Mode");
        tempMap.put(VarPlayerID.TOTAL_THERMY_KILLS, "Thermonuclear Smoke Devil");
        tempMap.put(VarPlayerID.TOTAL_COMPLETED_TOMBSOFAMASCUT, "Tombs of Amascut");
        tempMap.put(VarPlayerID.TOTAL_COMPLETED_TOMBSOFAMASCUT_EXPERT, "Tombs of Amascut: Expert Mode");
        tempMap.put(VarPlayerID.TOTAL_ZUK_KILLS, "TzKal-Zuk");
        tempMap.put(VarPlayerID.TOTAL_JAD_KILLS, "TzTok-Jad");
        tempMap.put(VarPlayerID.TOTAL_VARDORVIS_KILLS, "Vardorvis");
        tempMap.put(VarPlayerID.TOTAL_VENENATIS_KILLS, "Venenatis");
        tempMap.put(VarPlayerID.TOTAL_VETION_KILLS, "Vet'ion");
        tempMap.put(VarPlayerID.TOTAL_VORKATH_KILLS, "Vorkath");
        tempMap.put(VarPlayerID.TOTAL_WINTERTODT_KILLS, "Wintertodt");
        tempMap.put(VarPlayerID.TOTAL_YAMA_KILLS, "Yama");
        tempMap.put(VarPlayerID.TOTAL_ZALCANO_KILLS, "Zalcano");
        tempMap.put(VarPlayerID.TOTAL_SNAKEBOSS_KILLS, "Zulrah");
        tempMap.put(VarPlayerID.DOM_LEVEL_1_COMPLETIONS, "Doom of Mokhaiotl - W1");
        tempMap.put(VarPlayerID.DOM_LEVEL_2_COMPLETIONS, "Doom of Mokhaiotl - W2");
        tempMap.put(VarPlayerID.DOM_LEVEL_3_COMPLETIONS, "Doom of Mokhaiotl - W3");
        tempMap.put(VarPlayerID.DOM_LEVEL_4_COMPLETIONS, "Doom of Mokhaiotl - W4");
        tempMap.put(VarPlayerID.DOM_LEVEL_5_COMPLETIONS, "Doom of Mokhaiotl - W5");
        tempMap.put(VarPlayerID.DOM_LEVEL_6_COMPLETIONS, "Doom of Mokhaiotl - W6");
        tempMap.put(VarPlayerID.DOM_LEVEL_7_COMPLETIONS, "Doom of Mokhaiotl - W7");
        tempMap.put(VarPlayerID.DOM_LEVEL_8_PLUS_COMPLETIONS, "Doom of Mokhaiotl");
        tempMap.put(VarPlayerID.DOM_LEVEL_8_COMPLETIONS, "Doom of Mokhaiotl");
        tempMap.put(VarPlayerID.TOTAL_DUKE_SUCELLUS_AWAKENED_KILLS, "Awakened Duke");
        tempMap.put(VarPlayerID.TOTAL_LEVIATHAN_AWAKENED_KILLS, "Awakened Leviathan");
        tempMap.put(VarPlayerID.TOTAL_VARDORVIS_AWAKENED_KILLS, "Awakened Vardorvis");
        tempMap.put(VarPlayerID.TOTAL_WHISPERER_AWAKENED_KILLS, "Awakened Whisperer");
        tempMap.put(VarPlayerID.TOTAL_GEMSTONE_CRAB_KILLS, "Gemstone Crab");
        tempMap.put(VarPlayerID.JAD_CHALLENGE_1_COMPLETIONS, "Jad Challenge - 1");
        tempMap.put(VarPlayerID.JAD_CHALLENGE_2_COMPLETIONS, "Jad Challenge - 2");
        tempMap.put(VarPlayerID.JAD_CHALLENGE_3_COMPLETIONS, "Jad Challenge - 3");
        tempMap.put(VarPlayerID.JAD_CHALLENGE_4_COMPLETIONS, "Jad Challenge - 4");
        tempMap.put(VarPlayerID.JAD_CHALLENGE_5_COMPLETIONS, "Jad Challenge - 5");
        tempMap.put(VarPlayerID.JAD_CHALLENGE_6_COMPLETIONS, "Jad Challenge - 6");
        tempMap.put(VarPlayerID.JAD_CHALLENGE_LEAGUE_ONLY_COMPLETIONS, "Jad League Only Challenge");
        tempMap.put(VarPlayerID.TOTAL_COLOSSEUM_WAVES_COMPLETED, "Colossseum Wave Total");
        tempMap.put(VarPlayerID.TOTAL_COWBOSS_HARDMODE_KILLS, "Demonic Brutus");


        varpIdToBossName = tempMap;
    }

    private String bossKilled;
    private int bossKillCount;
    private boolean sendNextTick = false;

    @Override
    String endpoint() {
        return "/api/member/on_boss_killed";
    }
    
    @Override
    JsonObject body()
    {
        JsonObject body = new JsonObject();
        body.addProperty("boss_killed", bossKilled);

        if (bossKilled.equals("Doom of Mokhaiotl"))
        {
            int wave8Completions = client.getVarpValue(VarPlayerID.DOM_LEVEL_8_COMPLETIONS);
            int wave8PlusCompletions = client.getVarpValue(VarPlayerID.DOM_LEVEL_8_PLUS_COMPLETIONS);
            bossKillCount = wave8Completions + wave8PlusCompletions;
        }

        body.addProperty("prev_kc", bossKillCount - 1);

        JsonObject highscoreObject = new JsonObject();
        for (Map.Entry<Integer, String> entry : varpIdToBossName.entrySet())
        {
            int killCount = client.getVarpValue(entry.getKey());
            highscoreObject.addProperty(entry.getValue(), killCount);
        }

        // Doom is a combination of wave 8 and 8+
        int wave8Completions = client.getVarpValue(VarPlayerID.DOM_LEVEL_8_COMPLETIONS);
        int wave8PlusCompletions = client.getVarpValue(VarPlayerID.DOM_LEVEL_8_PLUS_COMPLETIONS);
        highscoreObject.addProperty("Doom of Mokhaiotl", wave8Completions + wave8PlusCompletions);

        body.add("highscore", highscoreObject);

        return body;
    }
    
    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged)
    {
        // One of our boss values has changed. We want to inform the server
        // that we've killed this boss, and update all of our kill counts
        if (varpIdToBossName.containsKey(varbitChanged.getVarpId()))
        {
            if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
            {
                // Ignore varbits on player first log-in
                return;
            }
            
            bossKilled = varpIdToBossName.get(varbitChanged.getVarpId());
            bossKillCount = client.getVarpValue(varbitChanged.getVarpId());

            if (bossKillCount <= 0)
            {
                // This can happen on login / hop, ignore it
                return;
            }

            // We may load several boss kill counts at once on login / hop, so we want to wait until 
            // the next tick to send the message to ensure we only send one message with the most up to date boss kill counts.
            sendNextTick = true;
        }
    }

    @Override
    String onRequestFailedMessage()
    {
        return "Failed to send Boss Killed message to the Valiance server.";
    }

    @Override
    String onSuccessResponseMessage()
    {
        log.info("Sent onBossKilled to server");
        return "Sent Boss Killed message to the Valiance server!";
    }

    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
        if (sendNextTick)
        {
            send();
            sendNextTick = false;
        }
    }
}
