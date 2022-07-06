package me.nithanim.quarkus.azure.generic.function.extension.deployment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
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

    Map<DotName, List<AnnotationInstance>> byClasses = groupByClasses(allAnnotations);

    for (List<AnnotationInstance> annotationsInOneClass : byClasses.values()) {
      var classInfo = getClass(annotationsInOneClass);
      List<MethodInfo> methods = getMethods(allAnnotations);

      FunctionClassBuildItem functionClassBuildItem = generateProxy(classInfo, methods);
      functionClassBuildItemBuildProducer.produce(functionClassBuildItem);
      generatedClasses.produce(getGeneratedClassBuildItem(functionClassBuildItem));
      unremovableBeanProducer.produce(getUnremovableBeanBuildItem(classInfo));
      log.info("Generated " + functionClassBuildItem.getClassName());

      transformers.produce(getBytecodeTransformerBuildItem(classInfo));
    }
  }

  private Map<DotName, List<AnnotationInstance>> groupByClasses(
      Collection<AnnotationInstance> allAnnotations) {
    return allAnnotations.stream()
        .collect(Collectors.groupingBy(a -> a.target().asMethod().declaringClass().name()));
  }

  private ClassInfo getClass(List<AnnotationInstance> annotationsInOneClass) {
    return annotationsInOneClass.get(0).target().asMethod().declaringClass();
  }

  private List<MethodInfo> getMethods(Collection<AnnotationInstance> allAnnotations) {
    return allAnnotations.stream()
        .map(AnnotationInstance::target)
        .map(AnnotationTarget::asMethod)
        .collect(Collectors.toList());
  }

  private FunctionClassBuildItem generateProxy(ClassInfo classInfo, List<MethodInfo> methods) {
    return new ProxyGenerator().generateProxy(classInfo, methods);
  }

  private GeneratedClassBuildItem getGeneratedClassBuildItem(
      FunctionClassBuildItem functionClassBuildItem) {
    return new GeneratedClassBuildItem(
        true,
        ProxyGeneratorUtil.getInternalName(functionClassBuildItem.getClassName()),
        functionClassBuildItem.getClassFile());
  }

  private BytecodeTransformerBuildItem getBytecodeTransformerBuildItem(ClassInfo classInfo) {
    return new BytecodeTransformerBuildItem(
        classInfo.name().toString(),
        (className, outputClassVisitor) -> new AnnotationRemoverClassVisitor(outputClassVisitor));
  }

  private UnremovableBeanBuildItem getUnremovableBeanBuildItem(ClassInfo classInfo) {
    return UnremovableBeanBuildItem.beanTypes(classInfo.name());
  }
}
