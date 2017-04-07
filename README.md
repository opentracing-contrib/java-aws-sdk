[![Build Status][ci-img]][ci]

# OpenTracing AWS Client Instrumentation
OpenTracing instrumentation for AWS clients.

## Installation

### Maven
pom.xml
```xml
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-aws-sdk</artifactId>
    <version>0.0.1</version>
</dependency>
```

You most likely need to exclude aws-java-sdk dependency and add own:
```xml
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-aws-sdk</artifactId>
    <version>0.0.1</version>
    <exclusions>
        <exclusion>
             <groupId>com.amazonaws</groupId>
             <artifactId>aws-java-sdk</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<dependency>
     <groupId>com.amazonaws</groupId>
     <artifactId>aws-java-sdk</artifactId>
    <version>{required version}</version>
</dependency>

```

## Usage

`DefaultSpanManager` is used to get active span

```java
// Instantiate tracer
Tracer tracer = ...

// Register tracer with GlobalTracer
GlobalTracer.register(tracer);

// Build AWS client with TracingRequestHandler e.g.
AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.US_WEST_2)
                .withRequestHandlers(new TracingRequestHandler())
                .build();

```


[ci-img]: https://travis-ci.org/opentracing-contrib/aws-sdk.svg?branch=master
[ci]: https://travis-ci.org/opentracing-contrib/aws-sdk

