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

package co.cask.cdap.internal.app.services;

import co.cask.cdap.api.SystemTableConfigurer;
import co.cask.cdap.spi.data.table.StructuredTableId;
import co.cask.cdap.spi.data.table.StructuredTableSpecification;
import co.cask.cdap.spi.data.table.field.FieldType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Configures system tables and prefixes all table ids to prevent clashes with CDAP platform tables.
 */
public class DefaultSystemTableConfigurer implements SystemTableConfigurer {
  public static final String PREFIX = "app_";
  private final List<StructuredTableSpecification> specs;

  public DefaultSystemTableConfigurer() {
    this.specs = new ArrayList<>();
  }

  @Override
  public void createTable(StructuredTableSpecification tableSpecification) {
    // prefix table ids to prevent clashes with the CDAP system.
    StructuredTableSpecification copied = new StructuredTableSpecification.Builder()
      .withFields(tableSpecification.getFieldTypes().toArray(new FieldType[0]))
      .withPrimaryKeys(tableSpecification.getPrimaryKeys().toArray(new String[0]))
      .withIndexes(tableSpecification.getIndexes().toArray(new String[0]))
      .withId(new StructuredTableId(PREFIX + tableSpecification.getTableId().getName()))
      .build();
    specs.add(copied);
  }

  public Collection<StructuredTableSpecification> getTableSpecs() {
    return Collections.unmodifiableList(specs);
  }
}
