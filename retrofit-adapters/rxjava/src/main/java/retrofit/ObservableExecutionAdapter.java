package retrofit;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

public final class ObservableExecutionAdapter implements CallAdapter {
  @Override public Type parseType(Type returnType) {
    if (Types.getRawType(returnType) != Observable.class) {
      return null;
    }
    if (returnType instanceof ParameterizedType) {
      return Types.getParameterUpperBound((ParameterizedType) returnType);
    }
    throw new IllegalStateException("Observable return type must be parameterized"
        + " as Observable<Foo> or Observable<? extends Foo>");
  }

  @Override public Object adapt(final Call<?> call) {
    return Observable.create(new Observable.OnSubscribe<Object>() {
      @Override public void call(final Subscriber<? super Object> subscriber) {
        call.enqueue(new Callback<Object>() {
          @Override public void success(Response<Object> response) {
            if (subscriber.isUnsubscribed()) {
              return;
            }

            // TODO
          }

          @Override public void failure(Throwable t) {
            if (subscriber.isUnsubscribed()) {
              return;
            }

            // TODO
          }

          @Override public void result(Result result) {
            switch (execution.classification()) {
              case RESULT:
                subscriber.onNext(result);
                subscriber.onCompleted();
                break;

              case RESPONSE:
                if (result.isError()) {
                  subscriber.onError(result.error());
                } else {
                  subscriber.onNext(result.response());
                  subscriber.onCompleted();
                }
                break;

              case USER:
                if (result.isError()) {
                  subscriber.onError(result.error());
                } else {
                  Response response = result.response();
                  if (response.isSuccess()) {
                    subscriber.onNext(response.body());
                    subscriber.onCompleted();
                  } else {
                    subscriber.onError(new IOException()); // TODO HTTP error
                  }
                }
                break;
            }
          }
        });

        // Attempt to cancel the call if it is still in-flight on unsubscription.
        subscriber.add(Subscriptions.create(new Action0() {
          @Override public void call() {
            call.cancel();
          }
        }));
      }
    });
  }
}
