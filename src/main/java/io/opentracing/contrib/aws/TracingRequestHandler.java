package io.opentracing.contrib.aws;


import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentracing.ActiveSpan;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;

/**
 * Tracing Request Handler
 */
public class TracingRequestHandler extends RequestHandler2 {

  private final HandlerContextKey<Span> contextKey = new HandlerContextKey<>("span");
  private final SpanContext parentContext; // for Async Client
  private final Tracer tracer;

  public TracingRequestHandler(Tracer tracer) {
    this.parentContext = null;
    this.tracer = tracer;
  }

  /**
   * In case of Async Client:  beforeRequest runs in separate thread therefore we need to inject
   * parent context to build chain
   *
   * @param parentContext parent context
   */
  public TracingRequestHandler(SpanContext parentContext, Tracer tracer) {
    this.parentContext = parentContext;
    this.tracer = tracer;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void beforeRequest(Request<?> request) {
    Tracer.SpanBuilder spanBuilder = tracer.buildSpan(request.getServiceName())
        .ignoreActiveSpan()
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

    ActiveSpan parentSpan = tracer.activeSpan();

    if (parentSpan != null) {
      spanBuilder.asChildOf(parentSpan);
    } else if (parentContext != null) {
      spanBuilder.asChildOf(parentContext);
    }

    Span span = spanBuilder.startManual();
    SpanDecorator.onRequest(request, span);

    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS,
        new TextMapInjectAdapter(request.getHeaders()));

    request.addHandlerContext(contextKey, span);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    Span span = request.getHandlerContext(contextKey);
    SpanDecorator.onResponse(response, span);
    span.finish();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    Span span = request.getHandlerContext(contextKey);
    SpanDecorator.onError(e, span);
    span.finish();
  }
}
