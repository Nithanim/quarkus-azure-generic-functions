package me.nithanim.quarkus.azure.generic.function.extension.deployment;

import java.util.ArrayList;
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
import me.nithanim.quarkus.azure.generic.function.extension.deployment.builditems.FunctionClassRequestBuildItem;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.builditems.FunctionClassResultBuildItem;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGenerator;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil;

public class FunctionProcessor {
  private static final Logger log = Logger.getLogger(FunctionProcessor.class);

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem("azure-functions-generic");
  }

  /**
   * Having one jar is easier than having to deal with dependencies in a lib folder.Though, The
   * Azure function host has version checks and yells at us for having "outdated" dependency because
   * it mistakes the version of this artifact as the version of jackson.
   */
  @BuildStep
  public UberJarRequiredBuildItem forceUberJar() {
    return new UberJarRequiredBuildItem();
  }

  /**
   * Searches the jandex index for methods that are supposed to be Azure functions entry points. It
   * then generates requests for them to be proxied for Quarkus.
   */
  @BuildStep
  List<FunctionClassRequestBuildItem> makeFunctionClassRequests(CombinedIndexBuildItem index) {
    DotName functionNameAnnotation = DotName.createSimple(FunctionName.class.getName());
    var allAnnotations = index.getIndex().getAnnotations(functionNameAnnotation);

    Map<DotName, List<AnnotationInstance>> byClasses = groupByClasses(allAnnotations);

    List<FunctionClassRequestBuildItem> buildItems = new ArrayList<>();
    for (List<AnnotationInstance> annotationsInOneClass : byClasses.values()) {
      var classInfo = getClass(annotationsInOneClass);
      List<MethodInfo> methods = getMethods(annotationsInOneClass);
      buildItems.add(new FunctionClassRequestBuildItem(classInfo, methods));
    }
    return buildItems;
  }

  /**
   * The original user classes would be unused and therefore removed. This step marks them
   * unremovable to include them in the resulting artifact.
   */
  @BuildStep
  List<UnremovableBeanBuildItem> markOriginalUserClassesUnremovable(
      List<FunctionClassRequestBuildItem> requests) {
    return requests.stream()
        .map(r -> UnremovableBeanBuildItem.beanTypes(r.getClassInfo().name()))
        .collect(Collectors.toList());
  }

  /**
   * Makes the generated function entry point proxies known to Quarkus as generated classes to
   * include in the build logic.
   */
  @BuildStep
  List<GeneratedClassBuildItem> publishGeneratedClasses(
      List<FunctionClassResultBuildItem> generated) {
    return generated.stream().map(this::getGeneratedClassBuildItem).collect(Collectors.toList());
  }

  /**
   * Removes the annotations from the original methods.
   *
   * <p>TODO most likely not needed except then something els makes use of them.
   */
  // @BuildStep
  List<BytecodeTransformerBuildItem> removeAnnotations(
      List<FunctionClassRequestBuildItem> generated) {
    return generated.stream()
        .map(r -> getBytecodeTransformerBuildItem(r.getClassInfo()))
        .collect(Collectors.toList());
  }

  @BuildStep
  void generateProxyClasses(
      List<FunctionClassRequestBuildItem> requests,
      BuildProducer<FunctionClassResultBuildItem> functionClassBuildItemBuildProducer
      // BuildProducer<AdditionalIndexedClassesBuildItem> additionalIndexedClassesProducer
      ) {

    for (FunctionClassRequestBuildItem request : requests) {
      var classInfo = request.getClassInfo();
      List<MethodInfo> methods = request.getMethodInfos();

      var functionClassResultBuildItem = generateProxy(classInfo, methods);
      log.debug("Generated " + functionClassResultBuildItem.getClassName());
      functionClassBuildItemBuildProducer.produce(functionClassResultBuildItem);
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

  private FunctionClassResultBuildItem generateProxy(
      ClassInfo classInfo, List<MethodInfo> methods) {
    return new ProxyGenerator().generateProxy(classInfo, methods);
  }

  private GeneratedClassBuildItem getGeneratedClassBuildItem(
      FunctionClassResultBuildItem functionClassResultBuildItem) {
    return new GeneratedClassBuildItem(
        true,
        ProxyGeneratorUtil.getInternalName(functionClassResultBuildItem.getClassName()),
        functionClassResultBuildItem.getClassFile());
  }

  private BytecodeTransformerBuildItem getBytecodeTransformerBuildItem(ClassInfo classInfo) {
    return new BytecodeTransformerBuildItem(
        classInfo.name().toString(),
        (className, outputClassVisitor) -> new AnnotationRemoverClassVisitor(outputClassVisitor));
  }
}
