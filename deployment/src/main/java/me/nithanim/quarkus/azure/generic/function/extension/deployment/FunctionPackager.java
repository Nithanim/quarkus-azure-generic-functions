package me.nithanim.quarkus.azure.generic.function.extension.deployment;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.azure.toolkit.lib.common.exception.AzureExecutionException;
import com.microsoft.azure.toolkit.lib.legacy.function.configurations.FunctionConfiguration;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.AnnotationHandler;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.AnnotationHandlerImpl;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.builditems.FunctionClassRequestBuildItem;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.builditems.FunctionClassResultBuildItem;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyInterfaceGenerator;

public class FunctionPackager {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .configure(SerializationFeature.INDENT_OUTPUT, true);

  @BuildStep
  public List<IndexDependencyBuildItem> indexAnnotations() {
    return List.of(
        new IndexDependencyBuildItem(
            "com.microsoft.azure.functions", "azure-functions-java-core-library"),
        new IndexDependencyBuildItem(
            "com.microsoft.azure.functions", "azure-functions-java-library"));
  }

  @BuildStep // (onlyIfNot = NativeBuild.class)
  public GeneratedFileSystemResourceBuildItem packageFunction(
      CurateOutcomeBuildItem curateOutcomeBuildItem,
      OutputTargetBuildItem outputTargetBuildItem,
      PackageConfig packageConfig,
      List<FunctionClassRequestBuildItem> functionClassRequestBuildItems,
      @SuppressWarnings("unused") JarBuildItem jar)
      throws IOException {

    Set<Method> generatedMethods = generateMethods(functionClassRequestBuildItems);

    String jarName = getRunnerJarName(outputTargetBuildItem, packageConfig);
    String relativePath = "../" + jarName;

    Map<String, FunctionConfiguration> functionConfigurations =
        generateFunctionConfigurations(generatedMethods, relativePath);

    generateAzureFunctionsDirectory(outputTargetBuildItem, functionConfigurations, jarName);

    // I don't know what I am doing, honestly.
    // This is a workaround such that >this< packaging method is called by the build process.
    // Otherwise, it would be dropped because it is not needed. Not sure how e.g. Docker or
    // native-image does it.
    return new GeneratedFileSystemResourceBuildItem("testtest", new byte[0]);
  }

  /** Generates fake proxy methods for generation of the config by the Azure maven plugin. */
  private Set<Method> generateMethods(
      List<FunctionClassRequestBuildItem> functionClassRequestBuildItems) {

    // Since the Azure maven plugin works with reflection, we load our fake proxies in a
    // classloader.
    // To prevent pollution, we make our own.
    var temporaryClassloader =
        new ClassLoader("AzureGenericFunctionsCL", Thread.currentThread().getContextClassLoader()) {
          public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
          }
        };

    Set<Method> methods = new HashSet<>();
    ProxyInterfaceGenerator proxyGenerator = new ProxyInterfaceGenerator();
    for (FunctionClassRequestBuildItem functionClassRequestBuildItem :
        functionClassRequestBuildItems) {

      FunctionClassResultBuildItem functionClassResultBuildItem =
          proxyGenerator.generateFakeProxy(
              functionClassRequestBuildItem.getClassInfo(),
              functionClassRequestBuildItem.getMethodInfos());
      byte[] classFile = functionClassResultBuildItem.getClassFile();

      Class<?> fakeProxyClass =
          temporaryClassloader.defineClass(
              functionClassResultBuildItem.getClassName().toString(), classFile);
      List<Method> fakeProxyEntryMethods = Arrays.asList(fakeProxyClass.getDeclaredMethods());
      methods.addAll(fakeProxyEntryMethods);
    }

    return methods;
  }

  /**
   * Passes the methods of the fake proxy to the Azure maven plugin library to create the expected
   * json for the Azure functions host.
   */
  private Map<String, FunctionConfiguration> generateFunctionConfigurations(
      Set<Method> generatedMethods, String relativePath) {
    AnnotationHandler handler = new AnnotationHandlerImpl();
    Map<String, FunctionConfiguration> functionConfigurations =
        getFunctionConfigurations(generatedMethods, handler);

    functionConfigurations.values().forEach(c -> c.setScriptFile(relativePath));
    return functionConfigurations;
  }

  /** Creates the filesystem structure that is expected as zip file by the Azure functions host. */
  private void generateAzureFunctionsDirectory(
      OutputTargetBuildItem outputTargetBuildItem,
      Map<String, FunctionConfiguration> functionConfigurations,
      String jarName)
      throws IOException {
    // TODO functions that were removed in code stay until a manual clean
    Path sourceJarPath = outputTargetBuildItem.getOutputDirectory().resolve(jarName);
    Path functionsPath = outputTargetBuildItem.getOutputDirectory().resolve("azure-functions");
    Files.createDirectories(functionsPath);
    copyHostJson(functionsPath);
    copyLocalSettingsJson(functionsPath);
    copyJar(functionsPath, sourceJarPath);
    for (var entry : functionConfigurations.entrySet()) {
      Path functionPath = functionsPath.resolve(entry.getKey());
      Files.createDirectories(functionPath);
      writeFunctionJson(functionPath, entry.getValue());
    }
  }

  @NotNull
  private String getRunnerJarName(
      OutputTargetBuildItem outputTargetBuildItem, PackageConfig packageConfig) {
    return outputTargetBuildItem.getBaseName() + packageConfig.runnerSuffix + ".jar";
  }

  private void copyHostJson(Path functionsPath) throws IOException {
    copyFile(functionsPath, "host.json");
  }

  private void copyLocalSettingsJson(Path functionsPath) throws IOException {
    copyFile(functionsPath, "local.settings.json");
  }

  private void copyFile(Path functionsPath, String fileName) throws IOException {
    Path targetPath = functionsPath.resolve(fileName);
    Path userFilePath = Paths.get(fileName);
    if (Files.exists(userFilePath)) {
      Files.copy(userFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    } else {
      try (var out = Files.newOutputStream(targetPath)) {
        try (var in = FunctionPackager.class.getClassLoader().getResourceAsStream("/" + fileName)) {
          if (in == null) {
            throw new IllegalStateException("File " + fileName + " not found on classpath!");
          }
          in.transferTo(out);
        }
      }
    }
  }

  private void copyJar(Path functionsPath, Path sourceJarPath) throws IOException {
    Path targetJarPath = functionsPath.resolve(sourceJarPath.getFileName());
    Files.copy(sourceJarPath, targetJarPath, StandardCopyOption.REPLACE_EXISTING);
  }

  private void writeFunctionJson(Path functionPath, FunctionConfiguration value)
      throws IOException {
    Files.write(
        functionPath.resolve("function.json"),
        objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
  }

  private Map<String, FunctionConfiguration> getFunctionConfigurations(
      Set<Method> generatedMethods, AnnotationHandler handler) {
    try {
      Map<String, FunctionConfiguration> configMap =
          handler.generateConfigurations(generatedMethods);
      configMap.values().forEach(FunctionConfiguration::validate);
      return configMap;
    } catch (AzureExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
