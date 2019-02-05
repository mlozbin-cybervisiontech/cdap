/*
 * Copyright © 2016 Cask Data, Inc.
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

package co.cask.cdap.operations.cdap;

import co.cask.cdap.api.metrics.MetricTimeSeries;
import co.cask.cdap.api.metrics.MetricsSystemClient;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.operations.OperationalStats;
import com.google.inject.Injector;
import org.apache.tephra.Transaction;
import org.apache.tephra.TransactionSystemClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * {@link OperationalStats} for reporting CDAP transaction statistics.
 */
public class CDAPTransactions extends AbstractCDAPStats implements CDAPTransactionsMXBean {

  private static final List<String> METRICS = Arrays.asList("system.committing.size", "system.committed.size");

  private TransactionSystemClient txClient;
  private MetricsSystemClient metricsSystemClient;
  private int numInvalidTx;
  private long readPointer;
  private long writePointer;
  private int numInProgressTx;
  private int numCommittingChangeSets;
  private int numCommittedChangeSets;

  @Override
  public void initialize(Injector injector) {
    txClient = injector.getInstance(TransactionSystemClient.class);
    metricsSystemClient = injector.getInstance(MetricsSystemClient.class);
  }

  @Override
  public String getStatType() {
    return "transactions";
  }

  @Override
  public long getReadPointer() {
    return readPointer;
  }

  @Override
  public long getWritePointer() {
    return writePointer;
  }

  @Override
  public int getNumInProgressTransactions() {
    return numInProgressTx;
  }

  @Override
  public int getNumInvalidTransactions() {
    return numInvalidTx;
  }

  @Override
  public int getNumCommittingChangeSets() {
    return numCommittingChangeSets;
  }

  @Override
  public int getNumCommittedChangeSets() {
    return numCommittedChangeSets;
  }

  @Override
  public void collect() throws Exception {
    Collection<MetricTimeSeries> collection = metricsSystemClient.query(Constants.Metrics.TRANSACTION_MANAGER_CONTEXT,
                                                                        METRICS);
    for (MetricTimeSeries metricTimeSeries : collection) {
      if (metricTimeSeries.getMetricName().equals("system.committing.size")) {
        numCommittingChangeSets = (int) aggregateMetricValue(metricTimeSeries);
      }
      if (metricTimeSeries.getMetricName().equals("system.committed.size")) {
        numCommittedChangeSets = (int) aggregateMetricValue(metricTimeSeries);
      }
    }

    Transaction transaction = txClient.startShort();
    readPointer = transaction.getReadPointer();
    writePointer = transaction.getWritePointer();
    numInProgressTx = transaction.getInProgress().length;
    numInvalidTx = transaction.getInvalids().length;
    txClient.abort(transaction);
  }
}
