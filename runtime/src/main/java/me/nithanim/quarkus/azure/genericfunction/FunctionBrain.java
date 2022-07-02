package me.nithanim.quarkus.azure.genericfunction;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;

/**
 * Every generated function will have calls to this class because it is easier to program here in
 * plain java than in bytecode directly. Though, this class might be better off being a base class
 * than a stand-alone one. But that could make the bytecode a bit more complex.
 */
public class FunctionBrain {
  /**
   * Called in each generated azure function entry point for each function invocation. Makes sure
   * that quarkus is initialized and the actual user function is available from the CDI container.
   *
   * @param function the "facade" entry point in front of the actual user-defined one
   * @param delegate the actual function class instance that was created by the user
   * @param <T> the type of the user function
   */
  public static <T> void detour(FunctionBaseInterface<T> function, T delegate) {
    if (delegate == null) {
      QuarkusBootstrap.ensureBootstrapped();
      Instance<T> newDelegate = CDI.current().select(function.getDelegateClass());
      function.setDelegate(newDelegate.get());
    }
  }
}
