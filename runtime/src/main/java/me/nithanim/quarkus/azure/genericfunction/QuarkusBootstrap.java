package me.nithanim.quarkus.azure.genericfunction;

public class QuarkusBootstrap extends QuarkusBootstrapBase {
  public static void ensureBootstrapped() {
    ensureQuarkusInitialized();

    if (bootstrapError) {
      throw new QuarkusBootstrapException(deploymentStatus);
    }
  }
}
