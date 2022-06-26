package me.nithanim.quarkus.azure.genericfunction;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.jboss.logging.Logger;

import io.quarkus.runtime.Application;

class QuarkusBootstrapBase {
  private static final Logger log = Logger.getLogger("io.quarkus.azure");

  protected static String deploymentStatus;
  protected static boolean started = false;
  protected static boolean bootstrapError = false;

  protected static void ensureQuarkusInitialized() {
    // The following will atomically call initQuarkus if this hasn't been done before,
    // and therefore make sure that deploymentStatus, started and bootstrapError are all set as
    // necessary
    QuarkusInitializer.ensureQuarkusInitialized();
  }

  private static void initQuarkus() {
    StringWriter error = new StringWriter();
    PrintWriter errorWriter = new PrintWriter(error, true);
    if (Application.currentApplication()
        == null) { // were we already bootstrapped?  Needed for mock azure unit testing.
      try {
        Class<?> appClass = Class.forName("io.quarkus.runner.ApplicationImpl");
        String[] args = {};
        Application app = (Application) appClass.getDeclaredConstructor().newInstance();
        app.start(args);
        errorWriter.println("Quarkus bootstrapped successfully.");
        started = true;
      } catch (Throwable ex) {
        bootstrapError = true;
        errorWriter.println("Quarkus bootstrap failed.");
        ex.printStackTrace(errorWriter);
        log.error("Quarkus bootstrap failed.", ex);
      }
    } else {
      errorWriter.println("Quarkus bootstrapped successfully.");
      started = true;
    }
    deploymentStatus = error.toString();
  }

  private static final class QuarkusInitializer {
    static {
      // Using an initializer block ensures that initQuarkus is called exactly once,
      // and is called atomically, thereby making it thread-safe.

      initQuarkus();
    }

    private static void ensureQuarkusInitialized() {
      // No code needed; the static initializer block will take care of the initialization.
      // This method exists to ensure that this class is loaded, and therefore Quarkus is
      // initialized.
    }
  }
}
