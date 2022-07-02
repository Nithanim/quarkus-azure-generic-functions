package me.nithanim.quarkus.azure.genericfunction;

/** All generated quarkus azure function entry points implement this interface. */
public interface FunctionBaseInterface<T> {
  /**
   * Sets the actual user-function object. All future calls to the entry points will be delegated
   * there directly.
   */
  void setDelegate(T delegate);

  /**
   * Returns the original user-function class. It is used to look up the initialized object from the
   * Quarkus CDI-container to delegate the function invocations to.
   */
  Class<T> getDelegateClass();
}
