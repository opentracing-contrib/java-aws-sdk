/*
 * Copyright 2017-2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.aws2;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;

public class Aws2Test {
  private static final MockTracer tracer = new MockTracer();
  private DynamoDBProxyServer server;

  @Before
  public void before() throws Exception {
    System.getProperties().setProperty("sqlite4java.library.path", "src/test/resources/libs");
    System.getProperties().setProperty("aws.region", "us-west-2");
    tracer.reset();

    final String[] localArgs = {"-inMemory", "-port", "8000"};
    server = ServerRunner.createServerFromCommandLineArgs(localArgs);
    server.start();
  }

  @After
  public void after() throws Exception {
    server.stop();
  }

  @Test
  public void testSyncClient() {
    final DynamoDbClient dbClient = buildClient();
    createTable(dbClient, "table-" + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE));

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals("CreateTableRequest", spans.get(0).operationName());

    assertNull(tracer.activeSpan());
  }

  @Test
  public void testAsyncClient() throws Exception {
    final DynamoDbAsyncClient dbClient = buildAsyncClient();
    final String tableName =
        "asyncRequest-" + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    final CompletableFuture<CreateTableResponse> createTableResultFuture = createTableAsync(
        dbClient, tableName);
    final CreateTableResponse result = createTableResultFuture.get(10, TimeUnit.SECONDS);
    assertThat(result.tableDescription().tableName()).isEqualTo(tableName);

    final List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    assertEquals("CreateTableRequest", spans.get(0).operationName());

    assertNull(tracer.activeSpan());
  }

  private static DynamoDbClient buildClient() {
    final AwsSessionCredentials awsCreds = AwsSessionCredentials
        .create("access_key_id", "secret_key_id", "session_token");
    return DynamoDbClient
        .builder()
        .endpointOverride(URI.create("http://localhost:8000"))
        .region(Region.US_WEST_2)
        .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
        .overrideConfiguration(
            ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(1)).build())
        .overrideConfiguration(
            builder -> builder.addExecutionInterceptor(new TracingExecutionInterceptor(
                tracer)))
        .build();
  }

  private static DynamoDbAsyncClient buildAsyncClient() {
    final AwsSessionCredentials awsCreds = AwsSessionCredentials
        .create("access_key_id", "secret_key_id", "session_token");
    final DynamoDbAsyncClient build = DynamoDbAsyncClient
        .builder()
        .endpointOverride(URI.create("http://localhost:8000"))
        .region(Region.US_WEST_2)
        .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
        .overrideConfiguration(ClientOverrideConfiguration.builder()
            .apiCallTimeout(Duration.ofSeconds(1)).build())
        .overrideConfiguration(
            builder -> builder.addExecutionInterceptor(new TracingExecutionInterceptor(tracer)))
        .build();
    return build;
  }

  private static void createTable(final DynamoDbClient dbClient, final String tableName) {
    final String partitionKeyName = tableName + "Id";
    final CreateTableRequest createTableRequest = CreateTableRequest.builder()
        .tableName(tableName)
        .keySchema(KeySchemaElement.builder().attributeName(partitionKeyName).keyType(KeyType.HASH)
            .build())
        .attributeDefinitions(
            AttributeDefinition.builder().attributeName(partitionKeyName).attributeType("S")
                .build())
        .provisionedThroughput(
            ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(5L).build())
        .build();

    dbClient.createTable(createTableRequest);
  }

  private static CompletableFuture<CreateTableResponse> createTableAsync(
      final DynamoDbAsyncClient dbClient, final String tableName) {
    final String partitionKeyName = tableName + "Id";
    final CreateTableRequest createTableRequest = CreateTableRequest
        .builder()
        .tableName(tableName).keySchema(
            KeySchemaElement.builder().attributeName(partitionKeyName).keyType(KeyType.HASH)
                .build())
        .attributeDefinitions(
            AttributeDefinition.builder().attributeName(partitionKeyName).attributeType("S")
                .build())
        .provisionedThroughput(
            ProvisionedThroughput.builder().readCapacityUnits(10L).writeCapacityUnits(5L).build())
        .build();

    return dbClient.createTable(createTableRequest);
  }
}
