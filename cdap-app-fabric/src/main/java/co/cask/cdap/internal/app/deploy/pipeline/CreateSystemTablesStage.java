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

package co.cask.cdap.internal.app.deploy.pipeline;

import co.cask.cdap.pipeline.AbstractStage;
import co.cask.cdap.spi.data.StructuredTableAdmin;
import co.cask.cdap.spi.data.TableAlreadyExistsException;
import co.cask.cdap.spi.data.table.StructuredTableSpecification;
import com.google.common.reflect.TypeToken;

import java.io.IOException;

/**
 * This {@link co.cask.cdap.pipeline.Stage} is responsible for creating system tables
 */
public class CreateSystemTablesStage extends AbstractStage<ApplicationDeployable> {
  private final StructuredTableAdmin structuredTableAdmin;

  public CreateSystemTablesStage(StructuredTableAdmin structuredTableAdmin) {
    super(TypeToken.of(ApplicationDeployable.class));
    this.structuredTableAdmin = structuredTableAdmin;
  }

  /**
   * Deploys dataset modules specified in the given application spec.
   *
   * @param input An instance of {@link ApplicationDeployable}
   */
  @Override
  public void process(ApplicationDeployable input) throws IOException {
    for (StructuredTableSpecification spec : input.getSystemTables()) {
      if (structuredTableAdmin.getSpecification(spec.getTableId()) == null) {
        try {
          structuredTableAdmin.create(spec);
        } catch (TableAlreadyExistsException e) {
          // this can happen if multiple applications are deployed at the same time
          // ignore this, as all that matters is that the table is created
        }
      }
    }

    // Emit the input to next stage.
    emit(input);
  }
}
