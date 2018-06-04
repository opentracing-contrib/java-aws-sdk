[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov] [![Released Version][maven-img]][maven]

# OpenTracing AWS Client Instrumentation
OpenTracing instrumentation for AWS clients.

## Installation

### Maven
pom.xml
```xml
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-aws-sdk</artifactId>
    <version>VERSION</version>
</dependency>
```

You most likely need to exclude aws-java-sdk dependency and add own:
```xml
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-aws-sdk</artifactId>
    <version>0.0.3</version>
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

```java
// Instantiate tracer
Tracer tracer = ...

// Build AWS client with TracingRequestHandler e.g.
AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.US_WEST_2)
                .withRequestHandlers(new TracingRequestHandler(tracer))
                .build();

```


[ci-img]: https://travis-ci.org/opentracing-contrib/java-aws-sdk.svg?branch=master
[ci]: https://travis-ci.org/opentracing-contrib/java-aws-sdk
[cov-img]: https://coveralls.io/repos/github/opentracing-contrib/java-aws-sdk/badge.svg?branch=master
[cov]: https://coveralls.io/github/opentracing-contrib/java-aws-sdk?branch=master
[maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-aws-sdk.svg
[maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-aws-sdk

