package com.launchdarkly.sdk.server;

import com.launchdarkly.client.Components;
import com.launchdarkly.client.Event;
import com.launchdarkly.client.EventProcessor;
import com.launchdarkly.client.EventProcessorInternals;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.interfaces.EventSender;
import com.launchdarkly.client.interfaces.EventSenderFactory;
import com.launchdarkly.client.interfaces.HttpConfiguration;
import com.launchdarkly.client.value.LDValue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.launchdarkly.sdk.server.TestValues.BASIC_USER;
import static com.launchdarkly.sdk.server.TestValues.CUSTOM_EVENT;
import static com.launchdarkly.sdk.server.TestValues.TEST_EVENTS_COUNT;

public class EventProcessorBenchmarks {
  private static final int EVENT_BUFFER_SIZE = 1000;
  private static final int FLAG_COUNT = 10;
  private static final int FLAG_VERSIONS = 3;
  private static final int FLAG_VARIATIONS = 2;
  
  @State(Scope.Thread)
  public static class BenchmarkInputs {
    // Initialization of the things in BenchmarkInputs does not count as part of a benchmark.
    final EventProcessor eventProcessor;
    final EventSender eventSender;
    final List<Event.FeatureRequest> featureRequestEventsWithoutTracking = new ArrayList<>();
    final List<Event.FeatureRequest> featureRequestEventsWithTracking = new ArrayList<>();
    final Random random;

    public BenchmarkInputs() {
      // MockEventSender does no I/O - it discards every event payload. So we are benchmarking
      // all of the event processing steps up to that point, including the formatting of the
      // JSON data in the payload.
      eventSender = new MockEventSender();
      
      eventProcessor = Components.sendEvents()
          .capacity(EVENT_BUFFER_SIZE)
          .eventSender(new MockEventSenderFactory())
          .createEventProcessor(TestValues.SDK_KEY, new LDConfig.Builder().build());
      
      random = new Random();
      
      for (int i = 0; i < TEST_EVENTS_COUNT; i++) {
        String flagKey = "flag" + random.nextInt(FLAG_COUNT);
        int version = random.nextInt(FLAG_VERSIONS) + 1;
        int variation = random.nextInt(FLAG_VARIATIONS);
        for (boolean trackEvents: new boolean[] { false, true }) {
          Event.FeatureRequest event = new Event.FeatureRequest(
              System.currentTimeMillis(),
              flagKey,
              BASIC_USER,
              version,
              variation,
              LDValue.of(variation),
              LDValue.ofNull(),
              null,
              null,
              trackEvents,
              null,
              false
              );
          (trackEvents ? featureRequestEventsWithTracking : featureRequestEventsWithoutTracking).add(event);
        }
      }
    }
    
    public String randomFlagKey() {
      return "flag" + random.nextInt(FLAG_COUNT);
    }
    
    public int randomFlagVersion() {
      return random.nextInt(FLAG_VERSIONS) + 1;
    }
    
    public int randomFlagVariation() {
      return random.nextInt(FLAG_VARIATIONS);
    }
  }
  
  @Benchmark
  public void summarizeFeatureRequestEvents(BenchmarkInputs inputs) throws Exception {
    for (Event.FeatureRequest event: inputs.featureRequestEventsWithoutTracking) {
      inputs.eventProcessor.sendEvent(event);
    }
    inputs.eventProcessor.flush();
    EventProcessorInternals.waitUntilInactive(inputs.eventProcessor);
  }

  @Benchmark
  public void featureRequestEventsWithFullTracking(BenchmarkInputs inputs) throws Exception {
    for (Event.FeatureRequest event: inputs.featureRequestEventsWithTracking) {
      inputs.eventProcessor.sendEvent(event);
    }
    inputs.eventProcessor.flush();
    EventProcessorInternals.waitUntilInactive(inputs.eventProcessor);
  }
  
  @Benchmark
  public void customEvents(BenchmarkInputs inputs) throws Exception {
    for (int i = 0; i < TEST_EVENTS_COUNT; i++) {
      inputs.eventProcessor.sendEvent(CUSTOM_EVENT);
    }
    inputs.eventProcessor.flush();
    EventProcessorInternals.waitUntilInactive(inputs.eventProcessor);
  }
  
  private static final class MockEventSender implements EventSender {
    private static final Result RESULT = new Result(true, false, null);
    
    @Override
    public void close() throws IOException {}

    @Override
    public Result sendEventData(EventDataKind arg0, String arg1, int arg2, URI arg3) {
      return RESULT;
    }
  }
  
  private static final class MockEventSenderFactory implements EventSenderFactory {
    @Override
    public EventSender createEventSender(String arg0, HttpConfiguration arg1) {
      return new MockEventSender();
    }
  }
}