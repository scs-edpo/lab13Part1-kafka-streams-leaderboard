package magicalpipelines;

import java.util.Properties;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.HostInfo;

public class App {
  public static void main(String[] args) {
    Topology topology = LeaderboardTopology.build();

    // we allow the following system properties to be overridden,
    // which allows us to run multiple instances of our app.
    // When running directly via IntelliJ, set additional parameters for the run configuration
    // VM Options: -Dhost=localhost -Dport=7000 -DstateDir=/tmp/kafka-streams
    String host = System.getProperty("host");
    Integer port = Integer.parseInt(System.getProperty("port"));
    String stateDir = System.getProperty("stateDir");
    String endpoint = String.format("%s:%s", host, port);

    // set the required properties for running Kafka Streams
    Properties props = new Properties();
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "dev");
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
    props.put(StreamsConfig.APPLICATION_SERVER_CONFIG, endpoint);
    props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);

    // build the topology
    System.out.println("Starting Videogame Leaderboard");
    KafkaStreams streams = new KafkaStreams(topology, props);
    // close Kafka Streams when the JVM shuts down (e.g. SIGTERM)
    Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    // start streaming!
    streams.start();

    // start the REST service
    HostInfo hostInfo = new HostInfo(host, port);
    LeaderboardService service = new LeaderboardService(hostInfo, streams);
    service.start();
  }
}
