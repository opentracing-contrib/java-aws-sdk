[![Build Status][ci-img]][ci] [![Coverage Status][cov-img]][cov] [![Released Version][maven-img]][maven] [![Apache-2.0 license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# OpenTracing AWS Client Instrumentation
OpenTracing instrumentation for AWS clients.

## Installation

### AWS SDK 1
pom.xml
```xml
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-aws-sdk-1</artifactId>
    <version>VERSION</version>
</dependency>
```

### AWS SDK 2
pom.xml
```xml
<dependency>
    <groupId>io.opentracing.contrib</groupId>
    <artifactId>opentracing-aws-sdk-2</artifactId>
    <version>VERSION</version>
</dependency>
```

## Usage

```java
// Instantiate tracer
Tracer tracer = ...
```

### AWS SDK 1

```java
// Build AWS client with TracingRequestHandler e.g.
AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.US_WEST_2)
                .withRequestHandlers(new TracingRequestHandler(tracer))
                .build();

```

### AWS SDK 2
```java
// Build AWS client with TracingExecutionInterceptor e.g.
S3Client s3Client = S3Client.builder().overrideConfiguration(
        builder -> builder.addExecutionInterceptor(new TracingExecutionInterceptor(tracer)))
        .build();
```

## License

[Apache 2.0 License](./LICENSE).

[ci-img]: https://travis-ci.org/opentracing-contrib/java-aws-sdk.svg?branch=master
[ci]: https://travis-ci.org/opentracing-contrib/java-aws-sdk
[cov-img]: https://coveralls.io/repos/github/opentracing-contrib/java-aws-sdk/badge.svg?branch=master
[cov]: https://coveralls.io/github/opentracing-contrib/java-aws-sdk?branch=master
[maven-img]: https://img.shields.io/maven-central/v/io.opentracing.contrib/opentracing-aws-sdk.svg
[maven]: http://search.maven.org/#search%7Cga%7C1%7Copentracing-aws-sdk

