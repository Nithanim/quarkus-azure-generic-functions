package me.nithanim.quarkus.azure.genericfunction;

/**
 *
 * @param <T>
 */
public interface FunctionBaseInterface<T> {
  void setDelegate(T delegate);

  Class<T> getDelegateClass();
}
