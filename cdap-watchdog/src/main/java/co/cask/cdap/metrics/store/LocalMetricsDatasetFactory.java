/*
 * Copyright 2017 Cask Data, Inc.
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

package co.cask.cdap.metrics.store;

import co.cask.cdap.api.dataset.DatasetAdmin;
import co.cask.cdap.api.dataset.DatasetDefinition;
import co.cask.cdap.api.dataset.table.TableProperties;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.data2.dataset2.lib.table.MetricsTable;
import com.google.inject.Inject;

/**
 * Standalone metrics dataset factory.
 */
public class LocalMetricsDatasetFactory extends DefaultMetricDatasetFactory {

  @Inject
  public LocalMetricsDatasetFactory(CConfiguration cConf,
                                    DatasetDefinition<MetricsTable, DatasetAdmin> metricsTableDefinition) {
    super(cConf, metricsTableDefinition);
  }

  @Override
  MetricsTable getOrCreateResolutionMetricsTable(String tableName, TableProperties.Builder props, int resolution) {
    return getOrCreateMetricsTable(tableName, props.build());
  }
}
