package io.opentracing.contrib.aws;


import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class SpanDecorator {

    public static void onRequest(Request request, Span span) {
        Tags.COMPONENT.set(span, "java-aws");
        Tags.HTTP_METHOD.set(span, request.getHttpMethod().name());
        Tags.HTTP_URL.set(span, request.getEndpoint().toString());
    }

    public static void onResponse(Response response, Span span) {
        Tags.HTTP_STATUS.set(span, response.getHttpResponse().getStatusCode());
    }

    public static void onError(Throwable throwable, Span span) {
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(errorLogs(throwable));
    }

    private static Map<String, Object> errorLogs(Throwable throwable) {
        Map<String, Object> errorLogs = new HashMap<>(4);
        errorLogs.put("event", Tags.ERROR.getKey());
        errorLogs.put("error.kind", throwable.getClass().getName());
        errorLogs.put("error.object", throwable);

        errorLogs.put("message", throwable.getMessage());

        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        errorLogs.put("stack", sw.toString());

        return errorLogs;
    }
}
