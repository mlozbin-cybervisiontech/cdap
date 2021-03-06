/*
 * Copyright © 2014-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.cdap.cdap.metrics.collect;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.cdap.cdap.api.metrics.MetricStore;
import io.cdap.cdap.api.metrics.MetricValues;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.id.Id;
import io.cdap.cdap.metrics.process.MessagingMetricsProcessorService;
import io.cdap.cdap.metrics.process.MessagingMetricsProcessorServiceFactory;
import org.apache.twill.common.Threads;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A {@link io.cdap.cdap.api.metrics.MetricsCollectionService} that writes to MetricsTable directly.
 * It also has a scheduling job that clean up old metrics periodically.
 */
@Singleton
public final class LocalMetricsCollectionService extends AggregatedMetricsCollectionService {

  private static final ImmutableMap<String, String> METRICS_PROCESSOR_CONTEXT =
    ImmutableMap.of(Constants.Metrics.Tag.NAMESPACE, Id.Namespace.SYSTEM.getId(),
                    Constants.Metrics.Tag.COMPONENT, Constants.Service.METRICS_PROCESSOR);

  private final CConfiguration cConf;
  private final MetricStore metricStore;
  private ScheduledExecutorService scheduler;
  private MessagingMetricsProcessorServiceFactory messagingMetricsProcessorFactory;
  private MessagingMetricsProcessorService messagingMetricsProcessor;

  @Inject
  LocalMetricsCollectionService(CConfiguration cConf, MetricStore metricStore) {
    super(TimeUnit.SECONDS.toMillis(cConf.getInt(Constants.Metrics.METRICS_MINIMUM_RESOLUTION_SECONDS)));
    this.cConf = cConf;
    this.metricStore = metricStore;
    metricStore.setMetricsContext(this.getContext(METRICS_PROCESSOR_CONTEXT));
  }

  /**
   * Setter method for the optional binding on the {@link MessagingMetricsProcessorServiceFactory}.
   */
  @Inject (optional = true)
  public void setMessagingMetricsProcessorFactory(MessagingMetricsProcessorServiceFactory factory) {
    this.messagingMetricsProcessorFactory = factory;
  }

  @Override
  protected void publish(Iterator<MetricValues> metrics) {
    List<MetricValues> metricValues = new ArrayList<>();
    while (metrics.hasNext()) {
      metricValues.add(metrics.next());
    }
    metricStore.add(metricValues);
  }

  @Override
  protected void startUp() throws Exception {
    super.startUp();

    // If there is a metrics processor for TMS, start it.
    if (messagingMetricsProcessorFactory != null) {
      messagingMetricsProcessor = messagingMetricsProcessorFactory.create(
        IntStream.range(0, cConf.getInt(Constants.Metrics.MESSAGING_TOPIC_NUM)).boxed().collect(Collectors.toSet()),
        getContext(METRICS_PROCESSOR_CONTEXT), 0);
      messagingMetricsProcessor.startAndWait();
    }

    // It will only do cleanup if the underlying table doesn't supports TTL.
    scheduler = Executors.newSingleThreadScheduledExecutor(Threads.createDaemonThreadFactory("metrics-cleanup"));
    long minimumRetentionSecs = cConf.getLong(Constants.Metrics.MINIMUM_RESOLUTION_RETENTION_SECONDS);
    // Try right away if there's anything to cleanup, we will then schedule based on the min retention interval
    scheduler.schedule(createCleanupTask(minimumRetentionSecs), 1, TimeUnit.SECONDS);
  }

  @Override
  protected void shutDown() throws Exception {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }

    // Shutdown the TMS metrics processor if present. This will flush all buffered metrics that were read from TMS
    Exception failure = null;
    try {
      if (messagingMetricsProcessor != null) {
        messagingMetricsProcessor.stopAndWait();
      }
    } catch (Exception e) {
      failure = e;
    }

    // Shutdown the local metrics process. This will flush all in memory metrics.
    try {
      super.shutDown();
    } catch (Exception e) {
      if (failure != null) {
        failure.addSuppressed(e);
      } else {
        failure = e;
      }
    }

    if (failure != null) {
      throw failure;
    }
  }

  /**
   * Creates a task for cleanup.
   *
   * @param nextScheduleDelaySecs The next schedule delay in seconds for the clean up task.
   */
  private Runnable createCleanupTask(long nextScheduleDelaySecs) {
    return new Runnable() {
      @Override
      public void run() {
        // We perform CleanUp only in LocalMetricsCollectionService , where TTL is NOT supported
        // by underlying data store.
        try {
          // delete metrics from metrics resolution table
          metricStore.deleteTTLExpired();
        } catch (Exception e) {
          throw Throwables.propagate(e);
        }
        // delete based on the min retention interval
        scheduler.schedule(this, nextScheduleDelaySecs, TimeUnit.SECONDS);
      }
    };
  }
}
