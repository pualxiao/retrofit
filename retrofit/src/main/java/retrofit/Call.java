package retrofit;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.ResponseBody;
import java.io.IOException;
import java.net.ProtocolException;

public final class Call<T> implements Cloneable {
  private final RestAdapter restAdapter;
  private final MethodInfo methodInfo;
  private final Object[] args;

  private volatile com.squareup.okhttp.Call rawCall;
  private boolean executed; // Guarded by this.

  Call(RestAdapter restAdapter, MethodInfo methodInfo, Object[] args) {
    this.restAdapter = restAdapter;
    this.methodInfo = methodInfo;
    this.args = args;
  }

  @SuppressWarnings("CloneDoesntCallSuperClone") // We are a final type & saved from state clearing.
  @Override public Call clone() {
    return new Call(restAdapter, methodInfo, args);
  }

  public void enqueue(final Callback<T> callback) {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already executed");
      executed = true;
    }

    final com.squareup.okhttp.Call call = createRawCall();
    rawCall = call;
    call.enqueue(new com.squareup.okhttp.Callback() {
      @Override public void onFailure(Request request, IOException e) {
        callback.failure(e); // TODO what if this throws?
      }

      @Override public void onResponse(com.squareup.okhttp.Response rawResponse) {
        Response<T> response;
        try {
          response = parseResponse(rawResponse);
        } catch (Throwable e) {
          callback.failure(e); // TODO what if this throws?
          return;
        }
        callback.success(response); // TODO what if this throws?
      }
    });
  }

  public Response<T> execute() throws IOException {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already executed");
      executed = true;
    }

    final com.squareup.okhttp.Call call = createRawCall();
    rawCall = call;
    return parseResponse(call.execute());
  }

  private com.squareup.okhttp.Call createRawCall() {
    String serverUrl = restAdapter.endpoint.url();
    RequestBuilder requestBuilder =
        new RequestBuilder(serverUrl, methodInfo, restAdapter.converter);
    requestBuilder.setArguments(args);
    Request request = requestBuilder.build();

    return restAdapter.client.newCall(request);
  }

  private Response<T> parseResponse(com.squareup.okhttp.Response rawResponse) throws IOException {
    ResponseBody rawBody = rawResponse.body();
    // Remove the body (the only stateful object) from the raw response so we can pass it along.
    rawResponse = rawResponse.newBuilder().body(null).build();

    T converted = null;
    ResponseBody body = null;

    try {
      int code = rawResponse.code();
      if (code < 200 || code >= 300) {
        // Buffer the entire body in the event of a non-2xx status to avoid future I/O.
        body = Utils.readBodyToBytesIfNecessary(rawBody);
      } else if (code == 204 || code == 205) {
        // HTTP 204 No Content "...response MUST NOT include a message-body"
        // HTTP 205 Reset Content "...response MUST NOT include an entity"
        if (rawBody.contentLength() > 0) {
          throw new ProtocolException("HTTP " + code + " response must not include body.");
        }
      } else {
        ExceptionCatchingRequestBody wrapped = new ExceptionCatchingRequestBody(rawBody);
        try {
          //noinspection unchecked
          converted = (T) restAdapter.converter.fromBody(wrapped, methodInfo.responseObjectType);
        } catch (RuntimeException e) {
          // If the underlying input stream threw an exception, propagate that rather than
          // indicating that it was a conversion exception.
          if (wrapped.threwException()) {
            throw wrapped.getThrownException();
          }

          throw e;
        }
      }
    } finally {
      rawBody.close();
    }

    return new Response<T>(rawResponse, converted, body, restAdapter.converter);
  }

  public void cancel() {
    com.squareup.okhttp.Call rawCall = this.rawCall;
    if (rawCall == null) {
      throw new IllegalStateException("enqueue or execute must be called first");
    }
    rawCall.cancel();
  }
}
