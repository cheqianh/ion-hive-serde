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

package software.amazon.ionhiveserde.integrationtest.tests

import junitparams.JUnitParamsRunner
import junitparams.Parameters
import junitparams.naming.TestCaseName
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import software.amazon.ion.*
import software.amazon.ionhiveserde.configuration.IonEncoding
import software.amazon.ionhiveserde.configuration.SerializeNullStrategy
import software.amazon.ionhiveserde.integrationtest.*
import software.amazon.ionhiveserde.integrationtest.docker.SHARED_DIR
import software.amazon.ionhiveserde.integrationtest.setup.TestData
import java.sql.ResultSet
import kotlin.test.*

/**
 * Test the mapping of null values between valid ion <-> hive type mapping.
 */
@RunWith(JUnitParamsRunner::class)
class NullMappingTest : Base() {
    /** Called by [Lifecycle] to setup test data */
    companion object : TestLifecycle {
        private const val nullDir = "$SHARED_DIR/input/type-mapping/null"
        private const val HDFS_PATH = "/data/input/type-mapping/null"

        private val types = TestData.typeMapping.keys().toList()

        private fun tableColumnName(index: Int) = "type_$index"

        override fun setup() {
            mkdir(nullDir)

            val writers = sequenceOf(
                    newTextWriterFromPath("$nullDir/null.ion"),
                    newBinaryWriterFromPath("$nullDir/null.10n"))

            writers.forEach { writer -> writer.use(::writeTestFile) }
        }

        override fun tearDown() {
            rm(nullDir)
        }

        private fun writeTestFile(writer: IonWriter) {
            // explicit null
            writer.stepIn(IonType.STRUCT)
            IntRange(1, types.count()).forEach { index ->
                writer.setFieldName(tableColumnName(index))
                writer.writeNull()
            }
            writer.stepOut()

            // explicit typed null
            writer.stepIn(IonType.STRUCT)
            types.forEachIndexed { index, s ->
                val values = TestData.typeMapping[s] as IonSequence
                writer.setFieldName(tableColumnName(index))
                writer.writeNull(values.first().type)
            }
            writer.stepOut()

            // implicit null
            writer.stepIn(IonType.STRUCT)
            writer.stepOut()
        }
    }

    /**
     * Creates a table with one column per hive type
     */
    private fun createTable(tableName: String, serdeProperties: Map<String, String> = emptyMap()) {
        val columns = types.asSequence()
                .mapIndexed { index, hiveType -> tableColumnName(index) to hiveType }
                .toMap()

        hive().createExternalTable(tableName, columns, HDFS_PATH, serdeProperties)
    }

    @Test
    fun jdbc() {
        val tableName = "nullJdbc"
        createTable(tableName)

        val typesList = types.toList()

        fun assertNullColumns(rs: ResultSet) {
            IntRange(1, rs.metaData.columnCount).forEach { index ->
                assertNull(rs.getObject(index), message = typesList[index - 1])
            }
        }

        hive().query("SELECT * FROM $tableName") { rs ->
            // 6 rows:
            // - explicit nulls, e.g. {field: null}
            // - explicit typed nulls, e.g. {field: null.int}
            // - implicit nulls, e.g. {}
            // in both binary and text

            IntRange(1, 6).forEach { _ ->
                assertTrue(rs.next())
                assertNullColumns(rs)
            }

            assertFalse(rs.next())
        }
    }

    fun encodings() = IonEncoding.values()

    private fun toFileTestTemplate(serdeProperties: Map<String, String>, assertions: (IonStruct) -> Unit) {
        val tableName = "nullToFile"
        createTable(tableName, serdeProperties)

        val rawBytes = hive().queryToFileAndRead("SELECT * FROM $tableName", serdeProperties)
        val datagram = DOM_FACTORY.loader.load(rawBytes)

        assertEquals(6, datagram.size)

        // serialized as empty structs
        datagram.map { it as IonStruct }.forEach(assertions)
    }

    private fun assertIonNull(expectedType: IonType, actual: IonValue) {
        assertNotNull(actual)
        assertTrue(actual.isNullValue)
        assertEquals(expectedType, actual.type)
    }

    @Test
    @Parameters(method = "encodings")
    @TestCaseName("[{index}] {method}: {0}")
    fun toFileDefaultSerializerOption(encoding: IonEncoding) {
        toFileTestTemplate(mapOf("ion.encoding" to encoding.name)) { assertTrue(it.isEmpty) }
    }

    @Test
    @Parameters(method = "encodings")
    @TestCaseName("[{index}] {method}: {0}")
    fun toFileDoNotSerializeNull(encoding: IonEncoding) {
        val serdeProperties = mapOf("ion.encoding" to encoding.name, "ion.serialize_null" to SerializeNullStrategy.OMIT.name)
        toFileTestTemplate(serdeProperties) { assertTrue(it.isEmpty) }
    }

    @Test
    @Parameters(method = "encodings")
    @TestCaseName("[{index}] {method}: {0}")
    fun toFileSerializeUntypedNull(encoding: IonEncoding) {
        val serdeProperties = mapOf("ion.encoding" to encoding.name, "ion.serialize_null" to SerializeNullStrategy.UNTYPED.name)
        toFileTestTemplate(serdeProperties) { struct ->
            assertEquals(types.size, struct.size())
            struct.forEach { value -> assertIonNull(IonType.NULL, value) }
        }
    }

    @Test
    @Parameters(method = "encodings")
    @TestCaseName("[{index}] {method}: {0}")
    fun toFileSerializeTypedNull(encoding: IonEncoding) {
        val serdeProperties = mapOf("ion.encoding" to encoding.name, "ion.serialize_null" to SerializeNullStrategy.TYPED.name)
        toFileTestTemplate(serdeProperties) { struct ->
            assertEquals(types.size, struct.size())
            struct.forEachIndexed { index, value ->
                when (types[index]) {
                    "BOOLEAN"                                        -> assertIonNull(IonType.BOOL, value)
                    "TINYINT", "INT", "BIGINT"                       -> assertIonNull(IonType.INT, value)
                    "FLOAT", "DOUBLE"                                -> assertIonNull(IonType.FLOAT, value)
                    "DECIMAL"                                        -> assertIonNull(IonType.DECIMAL, value)
                    "BINARY"                                         -> assertIonNull(IonType.BLOB, value)
                    "CHAR(20)", "VARCHAR(20)", "STRING"              -> assertIonNull(IonType.STRING, value)
                    "TIMESTAMP", "DATE"                              -> assertIonNull(IonType.TIMESTAMP, value)
                    "ARRAY<INT>"                                     -> assertIonNull(IonType.LIST, value)
                    "MAP<STRING,INT>", "STRUCT<foo:INT,bar:BOOLEAN>" -> assertIonNull(IonType.STRUCT, value)
                    else                                             -> Assert.fail("unknown type: ${types[index]}")
                }
            }
        }
    }
}
