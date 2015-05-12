package retrofit;

import java.lang.reflect.Type;

public interface ExecutionAdapter {
  /**
   * Return the user type for deserializing the response body if this handler is applicable for
   * {@code returnType}. Otherwise {@code null}.
   *
   * TODO example
   */
  Type parseType(Type returnType);

  Object execute(Execution execution);
}
