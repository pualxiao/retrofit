package retrofit;

import java.lang.reflect.Type;

public interface CallAdapter {
  Type parseType(Type returnType);
  Object adapt(Call<?> call);
}
