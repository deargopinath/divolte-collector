package io.divolte.server;

import io.divolte.server.CookieValues.CookieValue;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.io.Resources;

@ParametersAreNonnullByDefault
public abstract class BaseEventHandler {
    public static final AttachmentKey<CookieValue> PARTY_COOKIE_KEY = AttachmentKey.create(CookieValue.class);
    public static final AttachmentKey<CookieValue> SESSION_COOKIE_KEY = AttachmentKey.create(CookieValue.class);
    public static final AttachmentKey<String> PAGE_VIEW_ID_KEY = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> EVENT_ID_KEY = AttachmentKey.create(String.class);
    public static final AttachmentKey<Long> REQUEST_START_TIME_KEY = AttachmentKey.create(Long.class);
    public static final AttachmentKey<Long> COOKIE_UTC_OFFSET_KEY = AttachmentKey.create(Long.class);
    public static final AttachmentKey<Boolean> FIRST_IN_SESSION_KEY = AttachmentKey.create(Boolean.class);

    public final static String PARTY_ID_QUERY_PARAM = "p";
    public final static String NEW_PARTY_ID_QUERY_PARAM = "n";
    public final static String SESSION_ID_QUERY_PARAM = "s";
    public final static String FIRST_IN_SESSION_QUERY_PARAM = "f";
    public final static String PAGE_VIEW_ID_QUERY_PARAM = "v";
    public final static String EVENT_ID_QUERY_PARAM = "e";
    public final static String EVENT_TYPE_QUERY_PARAM = "t";
    public final static String CLIENT_TIMESTAMP_QUERY_PARAM = "c"; // chronos
    public final static String LOCATION_QUERY_PARAM = "l";
    public final static String REFERER_QUERY_PARAM = "r";
    public final static String VIEWPORT_PIXEL_WIDTH_QUERY_PARAM = "w";
    public final static String VIEWPORT_PIXEL_HEIGHT_QUERY_PARAM = "h";
    public final static String SCREEN_PIXEL_WIDTH_QUERY_PARAM = "i";
    public final static String SCREEN_PIXEL_HEIGHT_QUERY_PARAM = "j";
    public final static String DEVICE_PIXEL_RATIO = "k";


    private final ByteBuffer transparentImage;
    protected final IncomingRequestProcessingPool processingPool;

    public BaseEventHandler(final IncomingRequestProcessingPool processingPool) {
        this.processingPool = Objects.requireNonNull(processingPool);

        try {
            this.transparentImage = ByteBuffer.wrap(
                Resources.toByteArray(Resources.getResource("transparent1x1.gif"))
            ).asReadOnlyBuffer();
        } catch (final IOException e) {
            // Should throw something more specific than this.
            throw new RuntimeException("Could not load transparent image resource.", e);
        }
    }

    public final void handleEventRequest(HttpServerExchange exchange) throws Exception {
        // We only accept GET requests.
        if (exchange.getRequestMethod().equals(Methods.GET)) {
            /*
             * The source address can be fetched on-demand from the peer connection, which may
             * no longer be available after the response has been sent. So we materialize it here
             * to ensure it's available further down the chain.
             */
            exchange.setSourceAddress(exchange.getSourceAddress());

            doHandleEventRequest(exchange);
        } else {
            methodNotAllowed(exchange);
        }
    }

    protected abstract void doHandleEventRequest(final HttpServerExchange exchange) throws Exception;

    protected final void serveImage(final HttpServerExchange exchange) {
        exchange.setResponseCode(StatusCodes.ACCEPTED);

        final HeaderMap responseHeaders = exchange.getResponseHeaders();
        // The client ensures that each request is unique.
        // The cache-related headers are intended to prevent spurious reloads.
        // (As a GET request, agents are free to re-issue the request at will. This disturbs us.)
        responseHeaders
            .put(Headers.CONTENT_TYPE, "image/gif")
            .put(Headers.CACHE_CONTROL, "private, no-cache, proxy-revalidate")
            .put(Headers.PRAGMA, "no-cache")
            .put(Headers.EXPIRES, "Fri, 14 Apr 1995 11:30:00 GMT");

        exchange.getResponseSender().send(transparentImage.slice());
    }

    private static void methodNotAllowed(final HttpServerExchange exchange) {
        exchange.getResponseHeaders()
        .put(Headers.ALLOW, Methods.GET_STRING)
        .put(Headers.CONTENT_TYPE, "text/plain; charset=utf-8");

        exchange.setResponseCode(StatusCodes.METHOD_NOT_ALLOWED)
        .getResponseSender()
                .send("HTTP method " + exchange.getRequestMethod() + " not allowed.", StandardCharsets.UTF_8);
    }

    protected static class IncompleteRequestException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    protected static Optional<String> queryParamFromExchange(final HttpServerExchange exchange, final String param) {
        return Optional.ofNullable(exchange.getQueryParameters().get(param)).map(Deque::getFirst);
    }
}
