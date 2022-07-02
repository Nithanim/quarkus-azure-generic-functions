package me.nithanim.quarkus.azure.generic.function.extension.deployment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;

import com.microsoft.azure.functions.annotation.FunctionName;

import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.pkg.builditem.UberJarRequiredBuildItem;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGenerator;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil;

public class FunctionProcessor {
  private static final Logger log = Logger.getLogger(FunctionProcessor.class);

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem("azure-functions-generic");
  }

  @BuildStep
  public UberJarRequiredBuildItem forceUberJar() {
    return new UberJarRequiredBuildItem();
  }

  @BuildStep
  void applyMagic(
      BuildProducer<BytecodeTransformerBuildItem> transformers,
      BuildProducer<GeneratedClassBuildItem> generatedClasses,
      BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer,
      BuildProducer<FunctionClassBuildItem> functionClassBuildItemBuildProducer,
      // BuildProducer<AdditionalIndexedClassesBuildItem> additionalIndexedClassesProducer,
      CombinedIndexBuildItem index) {

    DotName functionNameAnnotation = DotName.createSimple(FunctionName.class.getName());
    var allAnnotations = index.getIndex().getAnnotations(functionNameAnnotation);

    Map<DotName, List<AnnotationInstance>> byClasses =
        allAnnotations.stream()
            .collect(Collectors.groupingBy(a -> a.target().asMethod().declaringClass().name()));

    for (List<AnnotationInstance> annotationsInOneClass : byClasses.values()) {
      var classInfo = annotationsInOneClass.get(0).target().asMethod().declaringClass();

      List<MethodInfo> methods =
          allAnnotations.stream()
              .map(AnnotationInstance::target)
              .map(AnnotationTarget::asMethod)
              .collect(Collectors.toList());

      FunctionClassBuildItem functionClassBuildItem =
          new ProxyGenerator().generateProxy(classInfo, methods);
      functionClassBuildItemBuildProducer.produce(functionClassBuildItem);
      GeneratedClassBuildItem item =
          new GeneratedClassBuildItem(
              true,
              ProxyGeneratorUtil.getInternalName(functionClassBuildItem.getClassName()),
              functionClassBuildItem.getClassFile());
      generatedClasses.produce(item);
      unremovableBeanProducer.produce(UnremovableBeanBuildItem.beanTypes(classInfo.name()));
      log.info("Generated " + functionClassBuildItem.getClassName());

      transformers.produce(
          new BytecodeTransformerBuildItem(
              classInfo.name().toString(),
              (className, outputClassVisitor) ->
                  new AnnotationRemoverClassVisitor(
                      /*new TraceClassVisitor(*/ outputClassVisitor /*, new PrintWriter(System.out))*/)));
    }
  }
}