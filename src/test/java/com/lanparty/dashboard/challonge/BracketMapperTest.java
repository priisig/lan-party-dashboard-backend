package com.lanparty.dashboard.challonge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class BracketMapperTest {

    @Test
    void mapsSingleEliminationSnapshot() throws IOException {
        String json = new ClassPathResource("demo/cs2-bracket.json").getContentAsString(StandardCharsets.UTF_8);
        Bracket bracket = BracketMapper.map(json);

        assertThat(bracket.underway()).isTrue();
        assertThat(bracket.rounds()).extracting(Bracket.Round::name).containsExactly("Viertelfinal", "Halbfinal", "Final");
        assertThat(bracket.currentRound()).isEqualTo("Viertelfinal");
        assertThat(bracket.participants()).hasSize(8);

        var first = bracket.rounds().getFirst().matches().getFirst();
        assertThat(first.player1()).isEqualTo(new Bracket.Side("Team Alpha", "13", true, false));
        assertThat(first.player2().score()).isEqualTo("8");

        var live = bracket.rounds().getFirst().matches().get(2);
        assertThat(live.live()).isTrue();

        var finalMatch = bracket.rounds().getLast().matches().getFirst();
        assertThat(finalMatch.player1().name()).isEqualTo("Sieger Match E");
        assertThat(finalMatch.player1().placeholder()).isTrue();
    }

    @Test
    void namesDoubleEliminationRoundsAndLosersBracket() {
        String json = """
                {"tournament":{"tournament_type":"double elimination","state":"underway","participants":[],
                 "matches":[
                  {"match":{"id":1,"round":1,"state":"complete","identifier":"A"}},
                  {"match":{"id":2,"round":2,"state":"open","identifier":"B"}},
                  {"match":{"id":3,"round":3,"state":"pending","identifier":"C"}},
                  {"match":{"id":4,"round":-1,"state":"open","identifier":"D"}},
                  {"match":{"id":5,"round":-2,"state":"pending","identifier":"E"}}]}}
                """;
        Bracket bracket = BracketMapper.map(json);
        assertThat(bracket.rounds()).extracting(Bracket.Round::name).containsExactly("Winner Halbfinal", "Winner Final", "Grand Final");
        assertThat(bracket.losersRounds()).extracting(Bracket.Round::name).containsExactly("Loser Runde 1", "Loser Final");
        assertThat(bracket.currentRound()).isEqualTo("Winner Final");
    }

    @Test
    void multiSetScoresShowSetsWon() {
        assertThat(BracketMapper.scores("2-1,1-2,3-0")).containsExactly("2", "1");
        assertThat(BracketMapper.scores("13-8")).containsExactly("13", "8");
        assertThat(BracketMapper.scores("-1-3")).containsExactly("-1", "3");
        assertThat(BracketMapper.scores("")).isNull();
    }

    @Test
    void normalizesChallongeUrls() {
        assertThat(ChallongeClient.normalizeSlug("vivolan_cs2")).isEqualTo("vivolan_cs2");
        assertThat(ChallongeClient.normalizeSlug("https://challonge.com/vivolan_cs2")).isEqualTo("vivolan_cs2");
        assertThat(ChallongeClient.normalizeSlug("challonge.com/de/vivolan_cs2/")).isEqualTo("vivolan_cs2");
        assertThat(ChallongeClient.normalizeSlug("https://vivo.challonge.com/cup?foo=1")).isEqualTo("vivo-cup");
        assertThat(ChallongeClient.normalizeSlug("  ")).isNull();
    }
}
