package com.lanparty.dashboard.challonge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.lanparty.dashboard.common.Json;

import tools.jackson.databind.JsonNode;

/** Converts a Challonge v1 tournament response (with participants and matches) into a {@link Bracket}. */
public final class BracketMapper {

    private BracketMapper() {
    }

    public static Bracket map(String snapshotJson) {
        JsonNode t = Json.parse(snapshotJson).path("tournament");
        String type = t.path("tournament_type").asString("single elimination");
        String state = t.path("state").asString("pending");

        Map<Long, String> names = new HashMap<>();
        List<String> participantNames = new ArrayList<>();
        for (JsonNode wrapper : t.path("participants")) {
            JsonNode p = wrapper.path("participant");
            String name = p.path("display_name").asString(p.path("name").asString("?"));
            names.put(p.path("id").asLong(), name);
            participantNames.add(name);
            // Group stage matches reference group player ids instead of participant ids.
            for (JsonNode gid : p.path("group_player_ids")) {
                names.put(gid.asLong(), name);
            }
        }

        Map<Long, String> identifiers = new HashMap<>();
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode wrapper : t.path("matches")) {
            JsonNode m = wrapper.path("match");
            // Skip group-stage matches of two-stage tournaments; the final stage is the bracket.
            if (!m.path("group_id").isNull() && !m.path("group_id").isMissingNode()) {
                continue;
            }
            matches.add(m);
            identifiers.put(m.path("id").asLong(), m.path("identifier").asString(""));
        }
        matches.sort(Comparator.comparingInt((JsonNode m) -> m.path("suggested_play_order").asInt(Integer.MAX_VALUE))
                .thenComparingLong(m -> m.path("id").asLong()));

        boolean elimination = type.contains("elimination");
        boolean doubleElim = type.equals("double elimination");
        TreeMap<Integer, List<Bracket.Match>> winners = new TreeMap<>();
        TreeMap<Integer, List<Bracket.Match>> losers = new TreeMap<>();
        Integer earliestOpenWinners = null;
        Integer earliestOpenLosers = null;

        for (JsonNode m : matches) {
            int round = m.path("round").asInt();
            Bracket.Match match = toMatch(m, names, identifiers);
            if (round < 0) {
                losers.computeIfAbsent(-round, r -> new ArrayList<>()).add(match);
                if (match.state().equals("open") && (earliestOpenLosers == null || -round < earliestOpenLosers)) {
                    earliestOpenLosers = -round;
                }
            } else {
                winners.computeIfAbsent(round, r -> new ArrayList<>()).add(match);
                if (match.state().equals("open") && (earliestOpenWinners == null || round < earliestOpenWinners)) {
                    earliestOpenWinners = round;
                }
            }
        }

        int maxWinners = winners.isEmpty() ? 0 : winners.lastKey();
        int maxLosers = losers.isEmpty() ? 0 : losers.lastKey();
        List<Bracket.Round> winnerRounds = new ArrayList<>();
        winners.forEach((n, list) -> winnerRounds.add(new Bracket.Round(n, roundName(n, maxWinners, elimination, doubleElim), list)));
        List<Bracket.Round> loserRounds = new ArrayList<>();
        losers.forEach((n, list) -> loserRounds.add(new Bracket.Round(-n, loserRoundName(n, maxLosers), list)));

        String current = null;
        if (earliestOpenWinners != null) {
            current = roundName(earliestOpenWinners, maxWinners, elimination, doubleElim);
        } else if (earliestOpenLosers != null) {
            current = loserRoundName(earliestOpenLosers, maxLosers);
        }
        return new Bracket(type, state, winnerRounds, loserRounds, current, participantNames);
    }

    static String roundName(int round, int maxRound, boolean elimination, boolean doubleElim) {
        if (!elimination) {
            return "Runde " + round;
        }
        int fromEnd = maxRound - round;
        if (doubleElim) {
            return switch (fromEnd) {
                case 0 -> "Grand Final";
                case 1 -> "Winner Final";
                case 2 -> "Winner Halbfinal";
                default -> "Runde " + round;
            };
        }
        return switch (fromEnd) {
            case 0 -> "Final";
            case 1 -> "Halbfinal";
            case 2 -> "Viertelfinal";
            case 3 -> "Achtelfinal";
            default -> "Runde " + round;
        };
    }

    static String loserRoundName(int round, int maxRound) {
        return round == maxRound ? "Loser Final" : "Loser Runde " + round;
    }

    private static Bracket.Match toMatch(JsonNode m, Map<Long, String> names, Map<Long, String> identifiers) {
        String state = m.path("state").asString("pending");
        long winnerId = m.path("winner_id").asLong(0);
        long p1 = m.path("player1_id").asLong(0);
        long p2 = m.path("player2_id").asLong(0);
        String[] scores = scores(m.path("scores_csv").asString(""));
        boolean hasScore = !state.equals("pending") && scores != null;
        Bracket.Side side1 = side(p1, m, "player1", names, identifiers, hasScore ? scores[0] : null, winnerId);
        Bracket.Side side2 = side(p2, m, "player2", names, identifiers, hasScore ? scores[1] : null, winnerId);
        boolean live = state.equals("open") && !m.path("underway_at").isNull() && !m.path("underway_at").isMissingNode();
        return new Bracket.Match(m.path("id").asLong(), m.path("identifier").asString(""), side1, side2, state, live);
    }

    private static Bracket.Side side(long playerId, JsonNode m, String prefix, Map<Long, String> names,
                                     Map<Long, String> identifiers, String score, long winnerId) {
        if (playerId != 0) {
            return new Bracket.Side(names.getOrDefault(playerId, "?"), score, playerId == winnerId, false);
        }
        long prereq = m.path(prefix + "_prereq_match_id").asLong(0);
        if (prereq != 0) {
            boolean loser = m.path(prefix + "_is_prereq_match_loser").asBoolean(false);
            String id = identifiers.getOrDefault(prereq, "?");
            return new Bracket.Side((loser ? "Verlierer Match " : "Sieger Match ") + id, null, false, true);
        }
        return new Bracket.Side("TBD", null, false, true);
    }

    /**
     * "13-8" → ["13", "8"]. Several sets ("2-1,1-2,3-0") → sets won per side.
     * Returns null when there is no score.
     */
    static String[] scores(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        String[] sets = csv.split(",");
        if (sets.length == 1) {
            String[] parts = splitSet(sets[0]);
            return parts == null ? null : parts;
        }
        int won1 = 0;
        int won2 = 0;
        for (String set : sets) {
            String[] parts = splitSet(set);
            if (parts == null) {
                continue;
            }
            try {
                int a = Integer.parseInt(parts[0]);
                int b = Integer.parseInt(parts[1]);
                if (a > b) {
                    won1++;
                } else if (b > a) {
                    won2++;
                }
            } catch (NumberFormatException ignored) {
                // keep counting the parsable sets
            }
        }
        return new String[] {String.valueOf(won1), String.valueOf(won2)};
    }

    /** Splits "13-8" and also negative scores like "-1-3". */
    private static String[] splitSet(String set) {
        String s = set.trim();
        int dash = s.indexOf('-', 1);
        if (dash < 0) {
            return null;
        }
        return new String[] {s.substring(0, dash), s.substring(dash + 1)};
    }
}
