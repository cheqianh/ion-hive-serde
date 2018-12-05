/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *      http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package software.amazon.ionhiveserde.configuration;

import java.util.List;
import java.util.Properties;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import software.amazon.ion.IonStruct;
import software.amazon.ion.IonSystem;
import software.amazon.ion.IonType;
import software.amazon.ionhiveserde.IonHiveSerDe;
import software.amazon.ionhiveserde.configuration.source.JavaPropertiesAdapter;
import software.amazon.ionhiveserde.configuration.source.RawConfiguration;
import software.amazon.ionpathextraction.PathExtractor;

/**
 * Encapsulates all SerDe properties.
 */
public class SerDeProperties extends BaseProperties {

    private final EncodingConfig encodingConfig;
    private final FailOnOverflowConfig failOnOverflowConfig;
    private final PathExtractionConfig pathExtractionConfig;
    private final SerializeAsConfig serializeAsConfig;
    private final SerializeNullConfig serializeNullConfig;
    private final TimestampOffsetConfig timestampOffsetConfig;

    /**
     * Constructor.
     *
     * @param properties {@link Properties} passed to {@link IonHiveSerDe#initialize(Configuration, Properties)}.
     * @param columnNames table column names in the same order as types
     * @param columnTypes table column types in the same order as names
     */
    public SerDeProperties(final Properties properties,
                    final List<String> columnNames,
                    final List<TypeInfo> columnTypes) {
        this(new JavaPropertiesAdapter(properties), columnNames, columnTypes);
    }

    private SerDeProperties(final RawConfiguration configuration,
                            final List<String> columnNames,
                            final List<TypeInfo> columnTypes) {
        super(configuration);

        encodingConfig = new EncodingConfig(configuration);
        failOnOverflowConfig = new FailOnOverflowConfig(configuration, columnNames);
        pathExtractionConfig = new PathExtractionConfig(configuration, columnNames);
        serializeAsConfig = new SerializeAsConfig(configuration, columnTypes);
        serializeNullConfig = new SerializeNullConfig(configuration);
        timestampOffsetConfig = new TimestampOffsetConfig(configuration);
    }

    /**
     * @see EncodingConfig#getEncoding()
     */
    public IonEncoding getEncoding() {
        return encodingConfig.getEncoding();
    }


    /**
     * @see TimestampOffsetConfig#getTimestampOffsetInMinutes()
     */
    public int getTimestampOffsetInMinutes() {
        return timestampOffsetConfig.getTimestampOffsetInMinutes();
    }

    /**
     * @see SerializeNullConfig#getSerializeNull()
     */
    public SerializeNullStrategy getSerializeNull() {
        return serializeNullConfig.getSerializeNull();
    }


    /**
     * @see FailOnOverflowConfig#failOnOverflowFor(String)
     */
    public boolean failOnOverflowFor(final String columnName) {
        return failOnOverflowConfig.failOnOverflowFor(columnName);
    }

    /**
     * @see SerializeAsConfig#serializationIonTypeFor(int)
     */
    public IonType serializationIonTypeFor(final int index) {
        return serializeAsConfig.serializationIonTypeFor(index);
    }

    /**
     * @see PathExtractionConfig#buildPathExtractor(IonStruct, IonSystem)
     */
    public PathExtractor buildPathExtractor(final IonStruct struct, final IonSystem domFactory) {
        return pathExtractionConfig.buildPathExtractor(struct, domFactory);
    }
}

