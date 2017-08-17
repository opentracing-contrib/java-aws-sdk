package io.opentracing.contrib.aws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import io.opentracing.ActiveSpan;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class TracingRequestHandlerTest {

  private static final MockTracer mockTracer = new MockTracer(new ThreadLocalActiveSpanSource(),
      MockTracer.Propagator.TEXT_MAP);
  private DynamoDBProxyServer server;

  @Before
  public void before() throws Exception {
    System.getProperties().setProperty("sqlite4java.library.path", "src/test/resources/libs");
    mockTracer.reset();

    final String[] localArgs = {"-inMemory", "-port", "8000"};
    server = ServerRunner.createServerFromCommandLineArgs(localArgs);
    server.start();
  }

  @After
  public void after() throws Exception {
    server.stop();
  }

  @Test
  public void two_requests() throws Exception {
    AmazonDynamoDB dbClient = buildClient();
    createTable(dbClient, "twoRequests-1");
    createTable(dbClient, "twoRequests-2");

    List<MockSpan> spans = mockTracer.finishedSpans();
    assertEquals(2, spans.size());

    checkSpans(spans);
    assertNotEquals(spans.get(0).context().traceId(), spans.get(1).context().traceId());

    assertNull(mockTracer.activeSpan());
  }

  @Test
  public void two_requests_with_parent() {
    AmazonDynamoDB dbClient = buildClient();

    try (ActiveSpan parent = mockTracer.buildSpan("parent-sync").startActive()) {
      createTable(dbClient, "with-parent-1");
      createTable(dbClient, "with-parent-2");
    }

    List<MockSpan> spans = mockTracer.finishedSpans();
    assertEquals(3, spans.size());

    MockSpan parent = getByOperationName(spans, "parent-sync");
    assertNotNull(parent);

    for (MockSpan span : spans) {
      if (parent.operationName().equals(span.operationName())) {
        continue;
      }
      assertEquals(parent.context().traceId(), span.context().traceId());
      assertEquals(parent.context().spanId(), span.parentId());
    }

    assertNull(mockTracer.activeSpan());
  }

  /**
   * In case of async requests parent-child relation is not created. There is no way to fix that
   * except explicitly set parent context in TracingRequestHandler
   */
  @Test
  public void async_requests_with_parent() throws Exception {
    AmazonDynamoDBAsync dbClient = buildAsyncClient();

    try (ActiveSpan parent = mockTracer.buildSpan("parent-async").startActive()) {
      Future<CreateTableResult> createTableResultFuture = createTableAsync(dbClient,
          "with-async-parent-1");
      Future<CreateTableResult> createTableResultFuture2 = createTableAsync(dbClient,
          "with-async-parent-2");

      createTableResultFuture.get(10, TimeUnit.SECONDS);
      createTableResultFuture2.get(10, TimeUnit.SECONDS);

      createTableAsync(dbClient, "with-async-parent-3").get(10, TimeUnit.SECONDS);
    }

    List<MockSpan> spans = mockTracer.finishedSpans();
    assertEquals(4, spans.size());

    MockSpan parent = getByOperationName(spans, "parent-async");
    assertNotNull(parent);

    for (MockSpan span : spans) {
      if (parent.operationName().equals(span.operationName())) {
        continue;
      }
      assertNotEquals(parent.context().traceId(), span.context().traceId());
      assertNotEquals(parent.context().spanId(), span.parentId());
    }
  }

  @Test
  public void two_async_requests() throws Exception {
    AmazonDynamoDBAsync dbClient = buildAsyncClient();

    Future<CreateTableResult> createTableResultFuture = createTableAsync(dbClient,
        "twoAsyncRequests-1");
    Future<CreateTableResult> createTableResultFuture2 = createTableAsync(dbClient,
        "twoAsyncRequests-2");

    CreateTableResult result = createTableResultFuture.get(10, TimeUnit.SECONDS);
    assertEquals("twoAsyncRequests-1", result.getTableDescription().getTableName());

    CreateTableResult result2 = createTableResultFuture2.get(10, TimeUnit.SECONDS);
    assertEquals("twoAsyncRequests-2", result2.getTableDescription().getTableName());

    List<MockSpan> spans = mockTracer.finishedSpans();
    assertEquals(2, spans.size());

    checkSpans(spans);
    assertNotEquals(spans.get(0).context().traceId(), spans.get(1).context().traceId());
    assertNull(mockTracer.activeSpan());
  }

  private void checkSpans(List<MockSpan> mockSpans) {
    for (MockSpan mockSpan : mockSpans) {
      assertEquals(Tags.SPAN_KIND_CLIENT, mockSpan.tags().get(Tags.SPAN_KIND.getKey()));
      assertEquals(SpanDecorator.COMPONENT_NAME, mockSpan.tags().get(Tags.COMPONENT.getKey()));
      assertEquals(0, mockSpan.generatedErrors().size());
      String operationName = mockSpan.operationName();
      assertTrue(operationName.equals("AmazonDynamoDBv2"));
    }
  }

  private AmazonDynamoDB buildClient() {
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "us-west-2");

    BasicAWSCredentials awsCreds = new BasicAWSCredentials("access_key_id", "secret_key_id");

    return AmazonDynamoDBClientBuilder.standard()
        .withEndpointConfiguration(endpointConfiguration)
        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
        .withRequestHandlers(new TracingRequestHandler(mockTracer)).build();
  }

  private AmazonDynamoDBAsync buildAsyncClient() {
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "us-west-2");

    BasicAWSCredentials awsCreds = new BasicAWSCredentials("access_key_id", "secret_key_id");

    return AmazonDynamoDBAsyncClientBuilder.standard()
        .withEndpointConfiguration(endpointConfiguration)
        .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
        .withRequestHandlers(new TracingRequestHandler(mockTracer)).build();
  }

  private void createTable(AmazonDynamoDB dbClient, String tableName) {
    String partitionKeyName = tableName + "Id";

    try {
      CreateTableRequest createTableRequest = new CreateTableRequest()
          .withTableName(tableName)
          .withKeySchema(new KeySchemaElement()
              .withAttributeName(partitionKeyName)
              .withKeyType(KeyType.HASH))
          .withAttributeDefinitions(new AttributeDefinition()
              .withAttributeName(partitionKeyName).withAttributeType("S"))
          .withProvisionedThroughput(new ProvisionedThroughput()
              .withReadCapacityUnits(10L)
              .withWriteCapacityUnits(5L));

      dbClient.createTable(createTableRequest);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private Future<CreateTableResult> createTableAsync(AmazonDynamoDBAsync dbClient,
      String tableName) {
    String partitionKeyName = tableName + "Id";

    CreateTableRequest createTableRequest = new CreateTableRequest()
        .withTableName(tableName)
        .withKeySchema(new KeySchemaElement()
            .withAttributeName(partitionKeyName)
            .withKeyType(KeyType.HASH))
        .withAttributeDefinitions(new AttributeDefinition()
            .withAttributeName(partitionKeyName).withAttributeType("S"))
        .withProvisionedThroughput(new ProvisionedThroughput()
            .withReadCapacityUnits(10L)
            .withWriteCapacityUnits(5L));

    return dbClient.createTableAsync(createTableRequest,
        new AsyncHandler<CreateTableRequest, CreateTableResult>() {
          @Override
          public void onError(Exception exception) {
            exception.printStackTrace();
          }

          @Override
          public void onSuccess(CreateTableRequest request, CreateTableResult createTableResult) {

          }
        });
  }

  private MockSpan getByOperationName(List<MockSpan> spans, String operationName) {
    for (MockSpan span : spans) {
      if (operationName.equals(span.operationName())) {
        return span;
      }
    }
    return null;
  }
}