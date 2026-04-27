package magicalpipelines;

import magicalpipelines.model.Player;
import magicalpipelines.model.Product;
import magicalpipelines.model.ScoreEvent;
import magicalpipelines.model.join.Enriched;
import magicalpipelines.model.join.ScoreWithPlayer;
import magicalpipelines.serialization.json.JsonSerdes;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Aggregator;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Initializer;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.apache.kafka.streams.state.KeyValueStore;

class LeaderboardTopology {

  public static Topology build() {
    // the builder is used to construct the topology
    StreamsBuilder builder = new StreamsBuilder();

    // register the score events stream
    KStream<String, ScoreEvent> scoreEvents =
        builder
            .stream("score-events", Consumed.with(Serdes.ByteArray(), JsonSerdes.ScoreEvent()))
            // now marked for re-partitioning
            .selectKey((k, v) -> v.getPlayerId().toString());

    // create the sharded players table
    KTable<String, Player> players =
        builder.table("players", Consumed.with(Serdes.String(), JsonSerdes.Player()));

    // create the global product table
    GlobalKTable<String, Product> products =
        builder.globalTable("products", Consumed.with(Serdes.String(), JsonSerdes.Product()));

    // serdes and types for joining score events with players. 
    Joined<String, ScoreEvent, Player> playerJoinParams =
        Joined.with(Serdes.String(), JsonSerdes.ScoreEvent(), JsonSerdes.Player());

    // combine a score event and its matching player into one object.
    ValueJoiner<ScoreEvent, Player, ScoreWithPlayer> scorePlayerJoiner =
        (score, player) -> new ScoreWithPlayer(score, player);

    // Join score events with players by key. 
    KStream<String, ScoreWithPlayer> withPlayers =
        scoreEvents.join(players, scorePlayerJoiner, playerJoinParams);





    // Extract the product id to look up the matching product.
    KeyValueMapper<String, ScoreWithPlayer, String> keyMapper =
        (leftKey, scoreWithPlayer) -> {
          return String.valueOf(scoreWithPlayer.getScoreEvent().getProductId());
        };

    // Combine the stream record with the matching product into one object.
    ValueJoiner<ScoreWithPlayer, Product, Enriched> productJoiner =
        (scoreWithPlayer, product) -> new Enriched(scoreWithPlayer, product);

    // Join each record with the product found by product id.
    KStream<String, Enriched> withProducts = withPlayers.join(products, keyMapper, productJoiner);

    // Print the enriched records for debugging.
    withProducts.print(Printed.<String, Enriched>toSysOut().withLabel("with-products"));



    
    // Regroup the stream by product id so each product gets its own leaderboard.
    KGroupedStream<String, Enriched> grouped =
        withProducts.groupBy(
            (key, value) -> value.getProductId().toString(),
            Grouped.with(Serdes.String(), JsonSerdes.Enriched()));
    // alternatively, use the following if you want to name the grouped repartition topic:
    // Grouped.with("grouped-enriched", Serdes.String(), JsonSerdes.Enriched()))

    // Start each product's aggregate with an empty HighScores object.
    Initializer<HighScores> highScoresInitializer = HighScores::new;

    // Update the aggregate whenever a new enriched score arrives for that product.
    Aggregator<String, Enriched, HighScores> highScoresAdder =
        (key, value, aggregate) -> aggregate.add(value);

    // Build a KTable where each product id maps to its current leaderboard.
    KTable<String, HighScores> highScores =
        grouped.aggregate(
            highScoresInitializer,
            highScoresAdder,
            Materialized.<String, HighScores, KeyValueStore<Bytes, byte[]>>
                // Store the aggregate in a named state store for interactive queries.
                as("leader-boards")
                .withKeySerde(Serdes.String())
                .withValueSerde(JsonSerdes.HighScores()));

    // Publish the latest leaderboard updates to the output topic.
    highScores.toStream().to("high-scores");

    return builder.build();
  }
}
