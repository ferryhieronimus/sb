package com.dynatrace.research.shufflebench;

import com.dynatrace.hash4j.hashing.Hashing;
import com.dynatrace.research.shufflebench.record.RandomRecordGenerator;
import com.dynatrace.research.shufflebench.record.ZipfRecordGenerator;
import com.dynatrace.research.shufflebench.record.Record;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class KafkaLoadGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaLoadGenerator.class);
  private static final long SEED = 0xd966c7902a8716f9L;

  private final String seedString;
  private final String kafkaBootstrapServers;
  private final String kafkaTopic;
  private final int executionTimeMs;
  private final int numSources;
  private final int recordsPerSecondAndSource;
  private final int recordFixedSize;
  private final int recordMinSize;
  private final int recordMaxSize;
  private final ScheduledExecutorService executor;
  private final List<KafkaSender> openKafkaSenders = new ArrayList<>();
  private final List<RecordSource> openRecordSources = new ArrayList<>();

  public KafkaLoadGenerator() {
    Config config = ConfigProvider.getConfig();
    this.executionTimeMs = config.getValue("execution.time.ms", Integer.class);
    this.seedString = config.getValue("seed.string", String.class);
    this.kafkaBootstrapServers = config.getValue("kafka.bootstrap.servers", String.class);
    this.kafkaTopic = config.getValue("kafka.topic", String.class);
    this.numSources = config.getValue("num.sources", Integer.class);
    this.recordsPerSecondAndSource = config.getValue("num.records.per.source.second", Integer.class);
    this.recordFixedSize = config.getValue("record.fixed.bytes", Integer.class);
    this.recordMinSize = config.getValue("record.size.min.bytes", Integer.class);
    this.recordMaxSize = config.getValue("record.size.max.bytes", Integer.class);
    int threadPoolSize = config.getValue("thread.pool.size", Integer.class);
    this.executor = new ScheduledThreadPoolExecutor(threadPoolSize);
  }

  public void startBlocking() throws InterruptedException {
    final KafkaSender kafkaSender = new KafkaSender(this.kafkaBootstrapServers, this.kafkaTopic);
    Config config = ConfigProvider.getConfig();

    for (int sourceId = 0; sourceId < numSources; sourceId++) {
      long seed = Hashing.komihash4_3()
          .hashStream()
          .putString(seedString)
          .putInt(sourceId)
          .getAsLong();

      String dist = config.getValue("record.size.distribution", String.class);
      Supplier<Record> generator;
      if ("zipf".equalsIgnoreCase(dist)) {
        double exponent = config.getValue("record.size.zipf.exponent", Double.class);
        generator = new ZipfRecordGenerator(seed, recordMinSize, recordMaxSize, exponent);
      } else {
        generator = new RandomRecordGenerator(seed, recordFixedSize);
      }

      RecordSource recordSource = new RecordSource(
          executor,
          recordsPerSecondAndSource,
          kafkaSender,
          generator,
          "source" + sourceId
      );
      openRecordSources.add(recordSource);
    }
    openKafkaSenders.add(kafkaSender);
    Thread.sleep(this.executionTimeMs);
  }

  public void stop() throws InterruptedException, IOException {
    for (RecordSource recordSource : openRecordSources) {
      recordSource.stop();
    }
    openRecordSources.clear();
    for (KafkaSender kafkaSender : openKafkaSenders) {
      kafkaSender.close();
    }
    openKafkaSenders.clear();
    executor.shutdown();
    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    final KafkaLoadGenerator kafkaLoadGenerator = new KafkaLoadGenerator();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LOGGER.info("Shut down load generator.");
      try {
        kafkaLoadGenerator.stop();
      } catch (InterruptedException | IOException e) {
        throw new RuntimeException(e);
      }
    }));
    kafkaLoadGenerator.startBlocking();
    kafkaLoadGenerator.stop();
  }
}