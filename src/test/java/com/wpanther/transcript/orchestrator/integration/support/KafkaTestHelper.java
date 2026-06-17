package com.wpanther.transcript.orchestrator.integration.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Minimal Kafka producer/consumer for integration tests. Sends JSON-serialised
 * payloads to a topic and polls a topic until a record matches a predicate
 * (or fails the test by throwing an {@link AssertionError} on timeout).
 *
 * <p>Consumers always use a unique group id (suffixed with a random UUID) and
 * {@code auto.offset.reset = earliest} so each call reads from the beginning
 * of the topic regardless of any prior consumer state.
 */
public class KafkaTestHelper {

    private final String brokers;
    private final ObjectMapper objectMapper;

    public KafkaTestHelper(String brokers, ObjectMapper objectMapper) {
        this.brokers = brokers;
        this.objectMapper = objectMapper;
    }

    /** Serialise {@code payload} to JSON and send synchronously to {@code topic}. */
    public void send(String topic, String key, Object payload) {
        try (KafkaProducer<String, String> p = producer()) {
            p.send(new ProducerRecord<>(topic, key, objectMapper.writeValueAsString(payload))).get();
        } catch (Exception e) {
            throw new RuntimeException("Send failed: " + topic, e);
        }
    }

    /**
     * Subscribe to {@code topic} with a fresh group id and poll until a record
     * deserialised to {@code type} satisfies {@code predicate} or the deadline
     * expires. On timeout throws {@link AssertionError} so it surfaces as a
     * test failure.
     */
    public <T> T pollFor(String topic, String groupId, Class<T> type,
            Predicate<T> predicate, Duration timeout) {
        try (KafkaConsumer<String, String> c = consumer(groupId)) {
            c.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (var rec : c.poll(Duration.ofMillis(500))) {
                    T msg = objectMapper.readValue(rec.value(), type);
                    if (predicate.test(msg)) return msg;
                }
            }
            throw new AssertionError("No matching message on " + topic + " within " + timeout);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private KafkaProducer<String, String> producer() {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(p);
    }

    private KafkaConsumer<String, String> consumer(String groupId) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(p);
    }
}
