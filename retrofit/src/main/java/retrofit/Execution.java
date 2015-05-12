package retrofit;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.ResponseBody;
import java.io.IOException;

public final class Execution {
  public enum Classification {
    /** {@link Result Result&lt;T>} */
    RESULT,
    /** {@link Response Response&lt;T>} */
    RESPONSE,
    /** Any raw user type {@code T}. */
    USER
  }

  private final RestAdapter restAdapter;
  private final MethodInfo methodInfo;
  private final Object[] args;

  Execution(RestAdapter restAdapter, MethodInfo methodInfo, Object[] args) {
    this.restAdapter = restAdapter;
    this.methodInfo = methodInfo;
    this.args = args;
  }

  public Classification classification() {
    return methodInfo.classification;
  }

  public com.squareup.okhttp.Call execute(final ExecutionCallback callback) {
    String serverUrl = restAdapter.endpoint.url();
    RequestBuilder requestBuilder =
        new RequestBuilder(serverUrl, methodInfo, restAdapter.converter);
    requestBuilder.setArguments(args);
    Request request = requestBuilder.build();

    final Call call = restAdapter.client.newCall(request);
    call.enqueue(new Callback() {
      @Override public void onFailure(Request request, IOException e) {
        callback.result(Result.fromError(e));
      }

      @Override public void onResponse(com.squareup.okhttp.Response response) {
        ResponseBody body = response.body();
        // Remove the body (the only stateful object) from the response so we can pass it along.
        response = response.newBuilder().body(null).build();

        int code = response.code();
        Object converted = null;
        try {
          if (code < 200 || code >= 300) {
            // Buffer the entire body in the even of a non-2xx status to avoid future I/O.
            body = Utils.readBodyToBytesIfNecessary(body);
          } else if (code == 204 || code == 205) {
            // HTTP 204 No Content "...response MUST NOT include a message-body"
            // HTTP 205 Reset Content "...response MUST NOT include an entity"
            if (body.contentLength() > 0) {
              // TODO is this an error passed to the callback?
              throw new IllegalStateException(code + " response must not include body.");
            }
            body.close();
            body = null;
          } else {
            ExceptionCatchingRequestBody wrapped = new ExceptionCatchingRequestBody(body);
            body = null;
            try {
              converted = restAdapter.converter.fromBody(wrapped, methodInfo.responseObjectType);
            } catch (RuntimeException e) {
              Throwable error = e;

              // If the underlying input stream threw an exception, propagate that rather than
              // indicating that it was a conversion exception.
              if (wrapped.threwException()) {
                error = wrapped.getThrownException();
              }

              // TODO include response in error?
              callback.result(Result.fromError(error));
              return; // TODO fix control flow
            }
          }
        } catch (IOException e) {
          // TODO include response in error?
          callback.result(Result.fromError(e));
          return; // TODO fix control flow
        }

        //noinspection unchecked
        callback.result(
            Result.fromResponse(new Response(response, converted, body, restAdapter.converter)));
      }
    });
    return call;
  }
}
