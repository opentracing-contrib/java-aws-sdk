package io.opentracing.contrib.aws;

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
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ListTablesResult;
import io.opentracing.contrib.spanmanager.DefaultSpanManager;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class TracingRequestHandlerTest {
    private static final MockTracer mockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP);
    private DynamoDBProxyServer server;

    @BeforeClass
    public static void init() throws MalformedURLException {
        GlobalTracer.register(mockTracer);
    }

    @Before
    public void before() throws Exception {
        System.getProperties().setProperty("sqlite4java.library.path", "src/test/resources/libs");
        DefaultSpanManager.getInstance().clear();
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
    public void twoRequests() throws Exception {
        AmazonDynamoDB dbClient = buildClient();
        firstRequest(dbClient);
        secondRequest(dbClient);

        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertEquals(2, mockSpans.size());

        checkSpans(mockSpans);
        assertNull(DefaultSpanManager.getInstance().current().getSpan());
    }

    @Test
    public void twoAsyncRequests() {
        AmazonDynamoDBAsync dbClient = buildAsyncClient();
        Future<ListTablesResult> listTablesResultFuture = firstRequestAsync(dbClient);
        Future<CreateTableResult> createTableResultFuture = secondRequestAsync(dbClient);

        try {
            ListTablesResult result = listTablesResultFuture.get(10, TimeUnit.SECONDS);
            assertTrue(result.getTableNames().size() >= 0);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }

        try {
            CreateTableResult result = createTableResultFuture.get(10, TimeUnit.SECONDS);
            assertEquals("test", result.getTableDescription().getTableName());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }


        List<MockSpan> mockSpans = mockTracer.finishedSpans();
        assertEquals(2, mockSpans.size());

        checkSpans(mockSpans);
        assertNull(DefaultSpanManager.getInstance().current().getSpan());
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
                .withRequestHandlers(new TracingRequestHandler()).build();
    }

    private AmazonDynamoDBAsync buildAsyncClient() {
        AwsClientBuilder.EndpointConfiguration endpointConfiguration =
                new AwsClientBuilder.EndpointConfiguration("http://localhost:8000", "us-west-2");

        BasicAWSCredentials awsCreds = new BasicAWSCredentials("access_key_id", "secret_key_id");

        return AmazonDynamoDBAsyncClientBuilder.standard()
                .withEndpointConfiguration(endpointConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .withRequestHandlers(new TracingRequestHandler()).build();
    }

    private void firstRequest(AmazonDynamoDB dbClient) {
        try {
            ListTablesResult result = dbClient.listTables();
            assertEquals(0, result.getTableNames().size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Future<ListTablesResult> firstRequestAsync(AmazonDynamoDBAsync dbClient) {
        return dbClient.listTablesAsync();
    }

    private void secondRequest(AmazonDynamoDB dbClient) {
        try {
            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName("test")
                    .withKeySchema(new KeySchemaElement()
                            .withAttributeName("testId")
                            .withKeyType(KeyType.HASH));
            dbClient.createTable(createTableRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Future<CreateTableResult> secondRequestAsync(AmazonDynamoDBAsync dbClient) {

        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName("test")
                .withKeySchema(new KeySchemaElement()
                        .withAttributeName("testId")
                        .withKeyType(KeyType.HASH));
        return dbClient.createTableAsync(createTableRequest, new AsyncHandler<CreateTableRequest, CreateTableResult>() {
            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
            }

            @Override
            public void onSuccess(CreateTableRequest request, CreateTableResult createTableResult) {

            }
        });
    }
}