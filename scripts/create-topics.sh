echo "Waiting for Kafka to come online..."
cub kafka-ready -b 'kafka:9092' 1 20
# create the game events topic
kafka-topics --create --bootstrap-server 'kafka:9092' --topic 'score-events' --replication-factor '1' --partitions '1'
# create the players topic
kafka-topics --create --bootstrap-server 'kafka:9092' --topic 'players' --replication-factor '1' --partitions '1'
# create the products topic
kafka-topics --create --bootstrap-server 'kafka:9092' --topic 'products' --replication-factor '1' --partitions '1'
# create the high-scores topic
kafka-topics --create --bootstrap-server 'kafka:9092' --topic 'high-scores' --replication-factor '1' --partitions '1'
sleep infinity
