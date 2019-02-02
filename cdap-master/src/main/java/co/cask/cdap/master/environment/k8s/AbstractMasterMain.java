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

import co.cask.cdap.api.metrics.MetricsCollectionService;
import co.cask.cdap.common.app.MainClassLoader;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.guice.ConfigModule;
import co.cask.cdap.common.guice.DFSLocationModule;
import co.cask.cdap.common.guice.IOModule;
import co.cask.cdap.common.logging.LoggingContext;
import co.cask.cdap.common.logging.LoggingContextAccessor;
import co.cask.cdap.common.namespace.guice.NamespaceQueryAdminModule;
import co.cask.cdap.common.options.Option;
import co.cask.cdap.common.options.OptionsParser;
import co.cask.cdap.common.runtime.DaemonMain;
import co.cask.cdap.common.utils.ProjectInfo;
import co.cask.cdap.logging.appender.LogAppender;
import co.cask.cdap.logging.appender.LogAppenderInitializer;
import co.cask.cdap.logging.appender.LogMessage;
import co.cask.cdap.master.environment.MasterEnvironmentExtensionLoader;
import co.cask.cdap.master.spi.environment.MasterEnvironment;
import co.cask.cdap.metrics.guice.MetricsClientRuntimeModule;
import co.cask.cdap.security.auth.context.AuthenticationContextModules;
import co.cask.cdap.security.authorization.AuthorizationEnforcementModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import org.apache.hadoop.conf.Configuration;
import org.apache.twill.discovery.DiscoveryService;
import org.apache.twill.discovery.DiscoveryServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 *
 */
public abstract class AbstractMasterMain extends DaemonMain {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractMasterMain.class);

  private final List<Service> services = new ArrayList<>();
  private Injector injector;

  /**
   * Helper method for sub-class to call from static void main.
   *
   * @param mainClass the class of the master main class implementation
   * @param args arguments to main
   * @param <T> type of the master main class
   * @throws Exception if execution failed
   */
  protected static <T extends AbstractMasterMain> void main(Class<T> mainClass, String[] args) throws Exception {
    ClassLoader classLoader = MainClassLoader.createFromContext();
    if (classLoader == null) {
      LOG.warn("Failed to create CDAP system ClassLoader. AuthEnforce annotation will not be rewritten.");
      mainClass.newInstance().doMain(args);
    } else {
      Thread.currentThread().setContextClassLoader(classLoader);

      // Use reflection to call doMain in the DaemonMain super class since the ClassLoader is different
      // We need to find the DaemonMain class from the super class chain
      Class<?> cls = classLoader.loadClass(mainClass.getName());
      Class<?> superClass = cls.getSuperclass();
      while (!DaemonMain.class.getName().equals(superClass.getName()) && !Object.class.equals(superClass)) {
        superClass = superClass.getSuperclass();
      }

      if (!DaemonMain.class.getName().equals(superClass.getName())) {
        // This should never happen
        throw new IllegalStateException("Main service class " + mainClass.getName() +
                                          " should inherit from " + DaemonMain.class.getName());
      }

      Method method = superClass.getDeclaredMethod("doMain", String[].class);
      method.setAccessible(true);
      method.invoke(cls.newInstance(), new Object[]{args});
    }
  }

  @Override
  public final void init(String[] args) {
    LOG.info("Initializing master service class {}", getClass().getName());

    ConfigOptions opts = new ConfigOptions();
    OptionsParser.init(opts, args, getClass().getSimpleName(), ProjectInfo.getVersion().toString(), System.out);

    CConfiguration cConf = CConfiguration.create();
    Configuration hConf = new Configuration();

    setupConfigurations(cConf, hConf);

    MasterEnvironmentExtensionLoader envExtLoader = new MasterEnvironmentExtensionLoader(cConf);
    MasterEnvironment masterEnv = envExtLoader.get(opts.envProvider);

    if (masterEnv == null) {
      throw new IllegalArgumentException("Unable to find a MasterEnvironment implementation with name "
                                           + opts.envProvider);
    }

    List<Module> modules = new ArrayList<>();
    modules.add(new ConfigModule(cConf, hConf));
    modules.add(new IOModule());
    modules.add(new MetricsClientRuntimeModule().getDistributedModules());
    modules.add(new DFSLocationModule());
    modules.add(new NamespaceQueryAdminModule());
    modules.add(new AuthorizationEnforcementModule().getDistributedModules());
    modules.add(new AuthenticationContextModules().getMasterModule());
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(DiscoveryService.class)
          .toProvider(new SupplierProviderBridge<>(masterEnv.getDiscoveryServiceSupplier()));
        bind(DiscoveryServiceClient.class)
          .toProvider(new SupplierProviderBridge<>(masterEnv.getDiscoveryServiceClientSupplier()));

        // TODO: Remove when there is a proper LogAppender to use in K8s
        bind(LogAppender.class).to(SysOutLogAppender.class).in(Scopes.SINGLETON);
      }
    });
    modules.addAll(getServiceModules());

    injector = Guice.createInjector(modules);

    // Initialize logging context
    injector.getInstance(LogAppenderInitializer.class).initialize();
    Optional.ofNullable(getLoggingContext()).ifPresent(LoggingContextAccessor::setLoggingContext);

    // Add Services
    services.add(injector.getInstance(MetricsCollectionService.class));
    addServices(injector, services);

    LOG.info("Master service {} initialized", getClass().getName());
  }

  @Override
  public final void start() {
    LOG.info("Starting all services for master service {}", getClass().getName());
    for (Service service : services) {
      LOG.info("Starting service {} in master service {}", service, getClass().getName());
      service.startAndWait();
    }
    LOG.info("All services for master service {} started", getClass().getName());
  }

  @Override
  public final void stop() {
    // Stop service in reverse order
    LOG.info("Stopping all services for master service {}", getClass().getName());
    for (Service service : Lists.reverse(services)) {
      LOG.info("Stopping service {} in master service {}", service, getClass().getName());
      try {
        service.stopAndWait();
      } catch (Exception e) {
        // Catch and log exception on stopping to make sure each service has a chance to stop
        LOG.warn("Exception raised when stopping service {} in master service {}", service, getClass().getName(), e);
      }
    }
    LOG.info("All services for master service {} stopped", getClass().getName());
  }

  @Override
  public final void destroy() {
    // no-op
  }

  @VisibleForTesting
  Injector getInjector() {
    return injector;
  }

  protected void setupConfigurations(CConfiguration cConf, Configuration hConf) {
    // no-op
  }

  /**
   * Adds {@link Service} to run
   * @param injector
   * @param services
   */
  protected abstract void addServices(Injector injector, List<? super Service> services);

  /**
   * Returns a {@link List} of Guice {@link Module} that this specific for this master service.
   */
  protected abstract List<Module> getServiceModules();

  @Nullable
  protected abstract LoggingContext getLoggingContext();

  private static final class ConfigOptions {
    @Option(name = "env", usage = "Name of the CDAP master environment extension provider")
    private String envProvider;
  }

  private static final class SupplierProviderBridge<T> implements Provider<T> {

    private final Supplier<T> supplier;

    private SupplierProviderBridge(Supplier<T> supplier) {
      this.supplier = supplier;
    }

    @Override
    public T get() {
      return supplier.get();
    }
  }

  /**
   * A {@link LogAppender} that just print to System.out.
   * TODO: Remove this class when there is a proper LogAppender for K8s.
   */
  private static final class SysOutLogAppender extends LogAppender {

    @Override
    protected void appendEvent(LogMessage logMessage) {
      System.out.println(logMessage);
    }
  }
}
