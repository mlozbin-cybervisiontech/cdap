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

package co.cask.cdap.metadata.elastic;

import co.cask.cdap.api.metadata.MetadataEntity;
import co.cask.cdap.api.metadata.MetadataScope;
import co.cask.cdap.spi.metadata.Metadata;
import co.cask.cdap.spi.metadata.ScopedName;
import org.elasticsearch.common.Strings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The document format that is indexed in Elastic.
 */
public class MetadataDocument {

  /**
   * Mapping for creating the ElasticSearch index. Although this is a property
   * of the Elasticsearch index, it is defined here, because it maps 1:1 to the
   * fields of this document class.
   */
  static final String MAPPING = ("{" +
    "  'properties': {" +
    "    'entity': {" +
    "      'enabled': 'false'" +
    "    }, " +
    "    'metadata': {" +
    "      'enabled': 'false'" +
    "    }, " +
    "    'hidden': {" +
    "      'type': 'boolean'" +
    "    }, " +
    "    'name': {" +
    "      'type': 'keyword'" +
    "    }, " +
    "    'namespace': {" +
    "      'type': 'keyword'" +
    "    }, " +
    "    'type': {" +
    "      'type': 'keyword'" +
    "    }, " +
    "    'user': {" +
    "      'type': 'text'," +
    "      'analyzer': 'text_analyzer'," +
    "      'copy_to': 'text'" +
    "    }, " +
    "    'system': {" +
    "      'type': 'text'," +
    "      'analyzer': 'text_analyzer'," +
    "      'copy_to': 'text'" +
    "    }, " +
    "    'text': {" +
    "      'type': 'text'," +
    "      'analyzer': 'text_analyzer'" +
    "    }, " +
    "    'props': {" +
    "      'type': 'nested'," +
    "      'properties': {" +
    "        'scope': {" +
    "          'type': 'keyword'" +
    "        }," +
    "        'name': {" +
    "          'type': 'keyword'" +
    "        }," +
    "        'value': {" +
    "          'type': 'text'," +
    "          'analyzer': 'text_analyzer'" +
    "        }" +
    "      }" +
    "    }" +
    "  }" +
    "}"
  ).replace('\'', '"');

  private final MetadataEntity entity;
  private final Metadata metadata;
  private final String namespace;
  private final String type;
  private final String name;
  private final boolean hidden;
  private final String user;
  private final String system;
  private final Set<Property> props;

  private MetadataDocument(MetadataEntity entity, Metadata metadata,
                           @Nullable String namespace,
                           String type, String name,
                           String user, String system,
                           Set<Property> props) {
    this.entity = entity;
    this.metadata = metadata;
    this.namespace = namespace;
    this.type = type;
    this.name = name;
    this.hidden = name.startsWith("_");
    this.user = user;
    this.system = system;
    this.props = props;
  }

  Metadata getMetadata() {
    return metadata;
  }

  /**
   * Create a builder for a MetadataDocument.
   */
  static MetadataDocument of(MetadataEntity entity, Metadata metadata) {
    return new Builder(entity).addMetadata(metadata).build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MetadataDocument that = (MetadataDocument) o;
    return Objects.equals(entity, that.entity) &&
      Objects.equals(metadata, that.metadata) &&
      Objects.equals(namespace, that.namespace) &&
      Objects.equals(type, that.type) &&
      Objects.equals(name, that.name) &&
      Objects.equals(user, that.user) &&
      Objects.equals(system, that.system) &&
      Objects.equals(props, that.props);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entity, metadata, namespace, type, name, user, system, props);
  }

  @Override
  public String toString() {
    return "MetadataDocument{" +
      "entity=" + entity +
      ", metadata=" + metadata +
      ", namespace='" + namespace + '\'' +
      ", type='" + type + '\'' +
      ", name='" + name + '\'' +
      ", hidden='" + hidden + '\'' +
      ", user='" + user + '\'' +
      ", system='" + system + '\'' +
      ", props=" + props +
      '}';
  }

  /**
   * Represents a property.
   */
  public static final class Property {
    private final String scope;
    private final String name;
    private final String value;

    Property(String scope, String name, String value) {
      this.scope = scope;
      this.name = name;
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Property property = (Property) o;
      return Objects.equals(scope, property.scope) &&
        Objects.equals(name, property.name) &&
        Objects.equals(value, property.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), scope, name, value);
    }

    @Override
    public String toString() {
      return scope + ':' + name + '=' + value;
    }
  }

  /**
   * A builder for MetadataDocuments.
   */
  public static class Builder {
    private final MetadataEntity entity;
    private Metadata metadata = Metadata.EMPTY;
    private final String namespace;
    private final String type;
    private final String name;
    private final List<String> userTags = new ArrayList<>();
    private final List<String> systemTags = new ArrayList<>();
    private final StringBuilder userText = new StringBuilder();
    private final StringBuilder systemText = new StringBuilder();
    private final Set<Property> properties = new HashSet<>();

    private Builder(MetadataEntity entity) {
      this.entity = entity;
      //noinspection ConstantConditions
      this.namespace = entity.containsKey("namespace") ? entity.getValue("namespace").toLowerCase() : null;
      this.type = entity.getType().toLowerCase();
      //noinspection ConstantConditions
      this.name = entity.getValue(entity.getType()).toLowerCase();
      append(MetadataScope.SYSTEM, this.type);
      append(MetadataScope.SYSTEM, this.name);
      addProperty(new ScopedName(MetadataScope.SYSTEM, this.type), this.name);
    }

    private void append(MetadataScope scope, String text) {
      (MetadataScope.USER == scope ? userText : systemText).append(' ').append(text);
    }

    private void addTag(ScopedName tag) {
      String name = tag.getName().toLowerCase();
      append(tag.getScope(), name);
      (MetadataScope.USER == tag.getScope() ? userTags : systemTags).add(name);
    }

    private void addProperty(ScopedName key, String value) {
      String name = key.getName().toLowerCase();
      value = value.toLowerCase();
      MetadataScope scope = key.getScope();
      append(scope, name);
      append(scope, value);
      properties.add(new Property(scope.name(), name, value));
    }

    Builder addMetadata(Metadata metadata) {
      this.metadata = metadata;
      metadata.getTags().forEach(this::addTag);
      metadata.getProperties().forEach(this::addProperty);
      return this;
    }

    MetadataDocument build() {
      properties.add(
        new Property(MetadataScope.USER.name(), "tags", Strings.collectionToDelimitedString(userTags, " ")));
      properties.add(
        new Property(MetadataScope.SYSTEM.name(), "tags", Strings.collectionToDelimitedString(systemTags, " ")));
      return
        new MetadataDocument(entity, metadata, namespace, type, name,
                             userText.toString(), systemText.toString(), properties);
    }
  }
}
