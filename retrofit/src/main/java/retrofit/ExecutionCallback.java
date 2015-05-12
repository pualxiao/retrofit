package retrofit;

public interface ExecutionCallback<T> {
  void result(Result<T> result);
}
