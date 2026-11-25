/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.mongodb;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonMapperProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.LimitApplicationResult;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.type.RowType;
import io.trino.spi.type.Type;
import io.trino.testing.TestingConnectorSession;
import io.trino.type.TypeDeserializer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMongoTableHandle
{
    private static final MongoColumnHandle COL_KEY1 = new MongoColumnHandle("key1", ImmutableList.of(), VARCHAR, false, false, Optional.empty());
    private static final MongoColumnHandle COL_KEY2 = new MongoColumnHandle("key2", ImmutableList.of(), INTEGER, false, false, Optional.empty());
    private static final MongoColumnHandle COL_VAL = new MongoColumnHandle("val", ImmutableList.of(), DOUBLE, false, false, Optional.empty());
    private static final MongoColumnHandle COL_COUNT = new MongoColumnHandle("col_count", ImmutableList.of(), BIGINT, false, false, Optional.empty());
    private static final MongoColumnHandle COL_SUM = new MongoColumnHandle("col_sum", ImmutableList.of(), DOUBLE, false, false, Optional.empty());
    private static final MongoColumnHandle COL_DISTINCT_COUNT = new MongoColumnHandle("col_distinct_count", ImmutableList.of(), BIGINT, false, false, Optional.empty());

    private final JsonCodec<MongoTableHandle> codec;

    public TestMongoTableHandle()
    {
        JsonMapper jsonMapper = new JsonMapperProvider()
                .withJsonDeserializers(ImmutableMap.of(Type.class, new TypeDeserializer(TESTING_TYPE_MANAGER)))
                .get();
        codec = new JsonCodecFactory(jsonMapper).jsonCodec(MongoTableHandle.class);
    }

    @Test
    public void testRoundTripWithoutQuery()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("schema", "table");
        MongoTableHandle expected = new MongoTableHandle(schemaTableName, remoteTableName, Optional.empty());

        String json = codec.toJson(expected);
        MongoTableHandle actual = codec.fromJson(json);

        assertThat(actual.schemaTableName())
                .isEqualTo(expected.schemaTableName());
    }

    @Test
    public void testRoundTripNonLowercaseWithoutQuery()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("Schema", "Table");
        MongoTableHandle expected = new MongoTableHandle(schemaTableName, remoteTableName, Optional.empty());

        String json = codec.toJson(expected);
        MongoTableHandle actual = codec.fromJson(json);

        assertThat(actual.schemaTableName())
                .isEqualTo(expected.schemaTableName());
    }

    @Test
    public void testRoundTripWithQuery()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("schema", "table");
        MongoTableHandle expected = new MongoTableHandle(schemaTableName, remoteTableName, Optional.of("{\"key\": \"value\"}"));

        String json = codec.toJson(expected);
        MongoTableHandle actual = codec.fromJson(json);

        assertThat(actual.schemaTableName())
                .isEqualTo(expected.schemaTableName());
    }

    @Test
    public void testRoundTripWithQueryHavingHelperFunction()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("schema", "table");
        MongoTableHandle expected = new MongoTableHandle(schemaTableName, remoteTableName, Optional.of("{timestamp: ISODate(\"2023-03-20T01:02:03.000Z\")}"));

        String json = codec.toJson(expected);
        MongoTableHandle actual = codec.fromJson(json);

        assertThat(actual.schemaTableName())
                .isEqualTo(expected.schemaTableName());
    }

    @Test
    public void testRoundTripWithProjectedColumns()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("Schema", "Table");
        Set<MongoColumnHandle> projectedColumns = ImmutableSet.of(
                new MongoColumnHandle("id", ImmutableList.of(), INTEGER, false, false, Optional.empty()),
                new MongoColumnHandle("address", ImmutableList.of("street"), VARCHAR, false, false, Optional.empty()),
                new MongoColumnHandle(
                        "user",
                        ImmutableList.of(),
                        RowType.from(ImmutableList.of(new RowType.Field(Optional.of("first"), VARCHAR), new RowType.Field(Optional.of("last"), VARCHAR))),
                        false,
                        false,
                        Optional.empty()),
                new MongoColumnHandle("creator", ImmutableList.of("databasename"), VARCHAR, false, true, Optional.empty()));

        MongoTableHandle expected = new MongoTableHandle(
                schemaTableName,
                remoteTableName,
                Optional.empty(),
                TupleDomain.all(),
                projectedColumns,
                OptionalInt.empty());

        String json = codec.toJson(expected);
        MongoTableHandle actual = codec.fromJson(json);

        assertThat(actual)
                .isEqualTo(expected);
    }

    @Test
    public void testRoundTripWithGroupedAggregation()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("schema", "table");

        MongoAccumulator countAll = new MongoAccumulator(COL_COUNT, "count", ImmutableList.of(), false);
        MongoAccumulator sum = new MongoAccumulator(COL_SUM, "sum", ImmutableList.of(COL_VAL), false);
        MongoAccumulator distinctCount = new MongoAccumulator(COL_DISTINCT_COUNT, "count", ImmutableList.of(COL_KEY2), true);

        MongoAggregation aggregation = new MongoAggregation(
                ImmutableList.of(COL_KEY1, COL_KEY2),
                ImmutableList.of(countAll, sum, distinctCount));

        MongoTableHandle expected = new MongoTableHandle(
                schemaTableName,
                remoteTableName,
                Optional.empty(),
                TupleDomain.all(),
                ImmutableSet.of(COL_KEY1, COL_KEY2),
                OptionalInt.empty(),
                Optional.of(aggregation));

        String json = codec.toJson(expected);
        MongoTableHandle actual = codec.fromJson(json);

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.aggregation()).isEqualTo(Optional.of(aggregation));
        assertThat(actual.aggregation().orElseThrow().isGlobalAggregation()).isFalse();
    }

    @Test
    public void testRoundTripWithGlobalAggregation()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("schema", "table");

        MongoAccumulator countAll = new MongoAccumulator(COL_COUNT, "count", ImmutableList.of(), false);
        MongoAggregation aggregation = new MongoAggregation(
                ImmutableList.of(),
                ImmutableList.of(countAll));

        MongoTableHandle expected = new MongoTableHandle(
                schemaTableName,
                remoteTableName,
                Optional.empty(),
                TupleDomain.all(),
                ImmutableSet.of(),
                OptionalInt.empty(),
                Optional.of(aggregation));

        String json = codec.toJson(expected);
        MongoTableHandle actual = codec.fromJson(json);

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.aggregation().orElseThrow().isGlobalAggregation()).isTrue();
    }

    @Test
    public void testBackwardCompatibilityWithoutAggregationField()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("schema", "table");
        MongoTableHandle handle = new MongoTableHandle(schemaTableName, remoteTableName, Optional.empty());

        String json = codec.toJson(handle);
        String jsonWithoutAggregation = json.replaceAll("\"aggregation\"\\s*:\\s*null,?", "");
        MongoTableHandle deserialized = codec.fromJson(jsonWithoutAggregation);

        assertThat(deserialized.aggregation()).isEmpty();
        assertThat(deserialized.schemaTableName()).isEqualTo(schemaTableName);
    }

    @Test
    public void testValueBasedEqualityAndHashCode()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("schema", "table");

        MongoAccumulator countAll = new MongoAccumulator(COL_COUNT, "count", ImmutableList.of(), false);
        MongoAggregation agg1 = new MongoAggregation(ImmutableList.of(COL_KEY1), ImmutableList.of(countAll));
        MongoAggregation agg2 = new MongoAggregation(ImmutableList.of(COL_KEY1), ImmutableList.of(countAll));
        MongoAggregation aggDifferentKey = new MongoAggregation(ImmutableList.of(COL_KEY2), ImmutableList.of(countAll));
        MongoAccumulator sum = new MongoAccumulator(COL_SUM, "sum", ImmutableList.of(COL_VAL), false);
        MongoAggregation aggDifferentAccumulator = new MongoAggregation(ImmutableList.of(COL_KEY1), ImmutableList.of(sum));

        MongoTableHandle handle1 = new MongoTableHandle(schemaTableName, remoteTableName, Optional.empty(), TupleDomain.all(), ImmutableSet.of(), OptionalInt.empty(), Optional.of(agg1));
        MongoTableHandle handle2 = new MongoTableHandle(schemaTableName, remoteTableName, Optional.empty(), TupleDomain.all(), ImmutableSet.of(), OptionalInt.empty(), Optional.of(agg2));
        MongoTableHandle unaggregated = new MongoTableHandle(schemaTableName, remoteTableName, Optional.empty());
        MongoTableHandle handleDiffKey = new MongoTableHandle(schemaTableName, remoteTableName, Optional.empty(), TupleDomain.all(), ImmutableSet.of(), OptionalInt.empty(), Optional.of(aggDifferentKey));
        MongoTableHandle handleDiffAccumulator = new MongoTableHandle(schemaTableName, remoteTableName, Optional.empty(), TupleDomain.all(), ImmutableSet.of(), OptionalInt.empty(), Optional.of(aggDifferentAccumulator));

        assertThat(handle1).isEqualTo(handle2);
        assertThat(handle1.hashCode()).isEqualTo(handle2.hashCode());

        assertThat(handle1).isNotEqualTo(unaggregated);
        assertThat(handle1).isNotEqualTo(handleDiffKey);
        assertThat(handle1).isNotEqualTo(handleDiffAccumulator);
    }

    @Test
    public void testToStringFormatting()
    {
        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("schema", "table");

        MongoTableHandle unaggregated = new MongoTableHandle(schemaTableName, remoteTableName, Optional.empty());
        assertThat(unaggregated.toString()).doesNotContain("groupingKeys");
        assertThat(unaggregated.toString()).doesNotContain("aggregations");

        MongoAccumulator countAll = new MongoAccumulator(COL_COUNT, "count", ImmutableList.of(), false);
        MongoAccumulator sum = new MongoAccumulator(COL_SUM, "sum", ImmutableList.of(COL_VAL), false);
        MongoAccumulator distinctCount = new MongoAccumulator(COL_DISTINCT_COUNT, "count", ImmutableList.of(COL_KEY2), true);
        MongoAggregation aggregation = new MongoAggregation(
                ImmutableList.of(COL_KEY1),
                ImmutableList.of(countAll, sum, distinctCount));

        MongoTableHandle aggregated = unaggregated.withAggregation(aggregation);
        assertThat(aggregated.toString()).contains("groupingKeys=[key1]");
        assertThat(aggregated.toString()).contains("aggregations=[col_count=count(*), col_sum=sum(val), col_distinct_count=count(DISTINCT key2)]");
    }

    @Test
    public void testDomainModelInvariants()
    {
        assertThatThrownBy(() -> new MongoAggregation(ImmutableList.of(), ImmutableList.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Both groupingKeys and accumulators are empty");

        MongoAccumulator countAll = new MongoAccumulator(COL_COUNT, "count", ImmutableList.of(), false);
        assertThat(countAll.isCountAll()).isTrue();
        assertThat(countAll.outputType()).isEqualTo(BIGINT);

        MongoAccumulator countKey = new MongoAccumulator(COL_COUNT, "count", ImmutableList.of(COL_KEY1), false);
        assertThat(countKey.isCountAll()).isFalse();

        MongoAccumulator sum = new MongoAccumulator(COL_SUM, "sum", ImmutableList.of(COL_VAL), false);
        assertThat(sum.isCountAll()).isFalse();
    }

    @Test
    public void testApplyLimitPreservesAggregation()
    {
        MongoSession mongoSession = new MongoSession(TESTING_TYPE_MANAGER, () -> null, new MongoClientConfig());
        MongoMetadata metadata = new MongoMetadata(mongoSession);

        SchemaTableName schemaTableName = new SchemaTableName("schema", "table");
        RemoteTableName remoteTableName = new RemoteTableName("schema", "table");
        MongoAccumulator countAll = new MongoAccumulator(COL_COUNT, "count", ImmutableList.of(), false);
        MongoAggregation aggregation = new MongoAggregation(ImmutableList.of(COL_KEY1), ImmutableList.of(countAll));

        MongoTableHandle handle = new MongoTableHandle(
                schemaTableName,
                remoteTableName,
                Optional.empty(),
                TupleDomain.all(),
                ImmutableSet.of(),
                OptionalInt.empty(),
                Optional.of(aggregation));

        ConnectorSession session = TestingConnectorSession.builder().build();
        Optional<LimitApplicationResult<ConnectorTableHandle>> result = metadata.applyLimit(session, handle, 10);

        assertThat(result).isPresent();
        MongoTableHandle limitedHandle = (MongoTableHandle) result.get().getHandle();
        assertThat(limitedHandle.aggregation()).isEqualTo(Optional.of(aggregation));
        assertThat(limitedHandle.limit()).isEqualTo(OptionalInt.of(10));
    }
}
