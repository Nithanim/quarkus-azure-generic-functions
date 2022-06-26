package me.nithanim.quarkus.azure.generic.function.extension.deployment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.azure.toolkit.lib.common.exception.AzureExecutionException;
import com.microsoft.azure.toolkit.lib.legacy.function.configurations.FunctionConfiguration;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.AnnotationHandler;
import com.microsoft.azure.toolkit.lib.legacy.function.handlers.AnnotationHandlerImpl;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

public class FunctionPackager {

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .configure(SerializationFeature.INDENT_OUTPUT, true);
  ;

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
      List<FunctionClassBuildItem> functionClassBuildItems,
      CombinedIndexBuildItem baseIndex,
      @SuppressWarnings("unused") JarBuildItem jar)
      throws IOException {

    Indexer indexer = new Indexer();
    indexer.indexClass(Override.class);
    indexer.indexClass(Target.class);
    indexer.indexClass(Retention.class);
    for (FunctionClassBuildItem functionClassBuildItem : functionClassBuildItems) {
      byte[] classFile = functionClassBuildItem.getClassFile();
      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(classFile);
      indexer.index(byteArrayInputStream);
    }
    Index generatedFunctionsIndex = indexer.complete();

    IndexView index = CompositeIndex.create(baseIndex.getIndex(), generatedFunctionsIndex);
    // IndexView index = baseIndex.getIndex();

    Set<MethodInfo> generatedClasses =
        functionClassBuildItems.stream()
            .flatMap(
                bi -> {
                  var clazz = index.getClassByName(bi.getClassName());
                  return bi.getMethodName().stream().map(clazz::firstMethod);
                })
            .collect(Collectors.toSet());

    AnnotationHandler handler = new AnnotationHandlerImpl();
    Map<String, FunctionConfiguration> functionConfigurations =
        getFunctionConfigurations(index, generatedClasses, handler);

    String jarName = outputTargetBuildItem.getBaseName() + packageConfig.runnerSuffix + ".jar";
    Path sourceJarPath = outputTargetBuildItem.getOutputDirectory().resolve(jarName);
    String relativePath = "../" + jarName;
    functionConfigurations.values().forEach(c -> c.setScriptFile(relativePath));

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

    // I don't know what I am doing, honestly.
    // This is a workaround such that >this< packaging method is called by the build process.
    // Otherwise, it would be dropped because it is not needed. Not sure how e.g. Docker or
    // native-image
    // does it.
    return new GeneratedFileSystemResourceBuildItem("testtest", new byte[0]);
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
      IndexView index, Set<MethodInfo> generatedClasses, AnnotationHandler handler) {
    try {
      Map<String, FunctionConfiguration> configMap =
          handler.generateConfigurations(index, generatedClasses);
      configMap.values().forEach(FunctionConfiguration::validate);
      return configMap;
    } catch (AzureExecutionException e) {
      throw new RuntimeException(e);
    }
  }
}
