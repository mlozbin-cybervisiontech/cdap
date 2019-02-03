/*
 * Copyright © 2019 Cask Data, Inc.
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

package co.cask.cdap.master.environment.k8s;

import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.messaging.guice.MessagingClientModule;
import co.cask.cdap.metrics.guice.MetricsStoreModule;
import com.google.common.util.concurrent.Service;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The main class to run metrics services, which includes both metrics processor and metrics query.
 */
public class MetricsServiceMain extends AbstractMasterMain {

  /**
   * Main entry point
   */
  public static void main(String[] args) throws Exception {
    main(MetricsServiceMain.class, args);
  }

  @Override
  protected void addServices(Injector injector, List<? super Service> services) {

  }

  @Override
  protected List<Module> getServiceModules() {
    return Arrays.asList(
      new MessagingClientModule(),
      new MetricsStoreModule()
//      new DataFabricModules(txClientId).getDistributedModules(),
//      new DataSetsModules().getDistributedModules(),
//      new MetricsProcessorTwillRunnable.MetricsProcessorModule(twillContext),
//      new MetricsProcessorStatusServiceModule(),
//      new AuditModule().getDistributedModules(),
//      new AbstractModule() {
//        @Override
//        protected void configure() {
//          bind(OwnerAdmin.class).to(DefaultOwnerAdmin.class);
//        }
//      }
    );
  }

  @Nullable
  @Override
  protected LoggingContext getLoggingContext() {
    return null;
  }
}
