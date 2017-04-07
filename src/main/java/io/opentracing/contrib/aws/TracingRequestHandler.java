package io.opentracing.contrib.aws;


import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.spanmanager.DefaultSpanManager;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;


public class TracingRequestHandler extends RequestHandler2 {
    private final HandlerContextKey<Span> contextKey = new HandlerContextKey<>("span");
    private final SpanContext parentContext; // for Async Client

    public TracingRequestHandler() {
        this.parentContext = null;
    }

    /**
     * In case of Async Client:  beforeRequest runs in separate thread therefore we need to inject
     * parent context to build chain
     *
     * @param parentContext parent context
     */
    public TracingRequestHandler(SpanContext parentContext) {
        this.parentContext = parentContext;
    }

    @Override
    public void beforeRequest(Request<?> request) {
        Tracer.SpanBuilder spanBuilder = GlobalTracer.get().buildSpan(request.getServiceName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

        Span parentSpan = DefaultSpanManager.getInstance().current().getSpan();

        if (parentSpan != null) {
            spanBuilder.asChildOf(parentSpan);
        } else if (parentContext != null) {
            spanBuilder.asChildOf(parentContext);
        }

        Span span = spanBuilder.start();
        SpanDecorator.onRequest(request, span);

        GlobalTracer.get().inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMapInjectAdapter(request.getHeaders()));

        request.addHandlerContext(contextKey, span);
    }

    @Override
    public void afterResponse(Request<?> request, Response<?> response) {
        Span span = request.getHandlerContext(contextKey);
        SpanDecorator.onResponse(response, span);
        span.finish();
    }

    @Override
    public void afterError(Request<?> request, Response<?> response, Exception e) {
        Span span = request.getHandlerContext(contextKey);
        SpanDecorator.onError(e, span);
        span.finish();
    }
}
