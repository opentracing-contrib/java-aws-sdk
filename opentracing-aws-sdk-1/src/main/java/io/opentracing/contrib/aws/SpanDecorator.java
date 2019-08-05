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
package io.opentracing.contrib.aws;


import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

class SpanDecorator {

  static final String COMPONENT_NAME = "java-aws-sdk";

  static void onRequest(Request request, Span span) {
    Tags.COMPONENT.set(span, COMPONENT_NAME);
    Tags.HTTP_METHOD.set(span, request.getHttpMethod().name());
    Tags.HTTP_URL.set(span, request.getEndpoint().toString());
    Tags.PEER_SERVICE.set(span, request.getServiceName());
  }

  static void onResponse(Response response, Span span) {
    Tags.HTTP_STATUS.set(span, response.getHttpResponse().getStatusCode());
  }

  static void onError(Throwable throwable, Span span) {
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
