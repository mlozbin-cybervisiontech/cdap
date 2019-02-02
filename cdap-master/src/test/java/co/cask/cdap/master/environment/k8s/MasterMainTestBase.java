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

import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.common.lang.ClassLoaders;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * Base class to help testing various master service main classes.
 */
public class MasterMainTestBase {

  @ClassRule
  public static final TemporaryFolder TEMP_FOLDER = new TemporaryFolder();

  protected static void initialize(Consumer<CConfiguration> confUpdater) throws IOException {
    CConfiguration cConf = CConfiguration.create();
    cConf.clear();
    cConf.set(Constants.CFG_LOCAL_DATA_DIR, TEMP_FOLDER.newFolder().getAbsolutePath());

    confUpdater.accept(cConf);

    // Write the "cdap-site.xml" to the "target" directory. It is determined by the current class classpath
    URL classPathURL = ClassLoaders.getClassPathURL(MessagingServiceMainTest.class);
    Assert.assertNotNull(classPathURL);
    File confDir = new File(classPathURL.getPath());
    try (Writer writer = Files.newBufferedWriter(new File(confDir, "cdap-site.xml").toPath())) {
      cConf.writeXml(writer);
    }
  }
}
