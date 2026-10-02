package com.lanparty.dashboard.challonge;

import java.util.List;

/**
 * Display model of a Challonge bracket, independent of Challonge's JSON.
 *
 * @param type          "single elimination", "double elimination", "round robin", "swiss", …
 * @param state         Challonge state: pending, underway, awaiting_review, complete
 * @param rounds        winners bracket (or all rounds for non-elimination formats)
 * @param losersRounds  losers bracket for double elimination, else empty
 * @param currentRound  name of the earliest round that still has open matches, e.g. "Viertelfinal"
 */
public record Bracket(String type, String state, List<Round> rounds, List<Round> losersRounds,
                      String currentRound, List<String> participants) {

    public record Round(int number, String name, List<Match> matches) {
    }

    public record Match(long id, String identifier, Side player1, Side player2, String state, boolean live) {
    }

    /** @param placeholder true when the slot is not decided yet ("Sieger Match 3") */
    public record Side(String name, String score, boolean winner, boolean placeholder) {
    }

    public boolean underway() {
        return "underway".equals(state) || "awaiting_review".equals(state);
    }
}
