package retrofit;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

public final class Call<T> {
  private final Execution execution;
  private final Executor callbackExecutor;

  private volatile com.squareup.okhttp.Call rawCall;
  private boolean executed; // Guarded by this.

  Call(Execution execution, Executor callbackExecutor) {
    this.execution = execution;
    this.callbackExecutor = callbackExecutor;
  }

  public void enqueue(final Callback<T> callback) {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }
    rawCall = execution.execute(new ExecutionCallback() {
      @SuppressWarnings("unchecked") // Reflection type lookup guarantees compatibility.
      @Override public void result(final Result result) {
        callbackExecutor.execute(new Runnable() {
          @Override public void run() {
            if (result.isError()) {
              callback.failure(result.error());
            } else {
              callback.success(result.response());
            }
          }
        });
      }
    });
  }

  public Response<T> execute() throws IOException {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Result<T>> resultRef = new AtomicReference<Result<T>>();
    com.squareup.okhttp.Call call = execution.execute(new ExecutionCallback<T>() {
      @Override public void result(Result<T> result) {
        resultRef.set(result);
        latch.countDown();
      }
    });

    try {
      latch.await();
    } catch (InterruptedException e) {
      call.cancel();
      Utils.sneakyRethrow(e);
    }

    Result<T> result = resultRef.get();
    if (result.isError()) {
      Utils.sneakyRethrow(result.error());
    }
    return result.response();
  }

  public void cancel() {
    com.squareup.okhttp.Call rawCall = this.rawCall;
    if (rawCall == null) {
      throw new IllegalStateException("enqueue must be called first");
    }
    rawCall.cancel();
  }
}
