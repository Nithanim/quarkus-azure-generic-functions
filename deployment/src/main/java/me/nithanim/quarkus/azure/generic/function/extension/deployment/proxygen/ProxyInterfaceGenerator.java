package me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen;

import java.util.List;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkus.deployment.QuarkusClassWriter;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.builditems.FunctionClassResultBuildItem;

import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.AnnotationUtil.visitAnnotations;
import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil.getDescriptor;
import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil.getExceptions;
import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil.getInternalName;

/**
 * Generates a fake version of the proxy class that does nothing but has the identical name. This
 * enables loading the resulting class for reflectively generating the json config without having to
 * load anything else. (The real proxy references the user code that we do NOT want to load.)
 */
public class ProxyInterfaceGenerator {
  public FunctionClassResultBuildItem generateFakeProxy(
      ClassInfo classInfo, List<MethodInfo> methodInfos) {
    DotName originalClassName = classInfo.name();
    DotName proxyClassName = makeProxyClassName(originalClassName);

    String proxyClassInternalName = getInternalName(proxyClassName);
    ClassWriter cw = new QuarkusClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    visitProxyClass(methodInfos, proxyClassInternalName, cw);
    return new FunctionClassResultBuildItem(
        proxyClassName,
        methodInfos.stream().map(MethodInfo::name).collect(Collectors.toList()),
        cw.toByteArray());
  }

  private void visitProxyClass(
      List<MethodInfo> methodInfos, String proxyClassInternalName, ClassVisitor cv) {

    visitClassHeader(cv, proxyClassInternalName);

    for (MethodInfo methodInfo : methodInfos) {
      visitMethodEntry(cv, methodInfo);
    }

    cv.visitEnd();
  }

  private void visitMethodEntry(ClassVisitor cv, MethodInfo methodInfo) {
    String descriptor = getDescriptor(methodInfo);
    String[] exceptions = getExceptions(methodInfo);
    MethodVisitor mv =
        cv.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            methodInfo.name(),
            descriptor,
            null,
            exceptions);

    visitAnnotations(methodInfo, mv);
    mv.visitEnd();
  }

  private DotName makeProxyClassName(DotName originalClassName) {
    return DotName.createComponentized(
        originalClassName.prefix(), originalClassName.local() + "QuarkusEntryProxy");
  }

  private void visitClassHeader(ClassVisitor cv, String proxyClassInternalName) {
    cv.visit(
        Opcodes.V1_5,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE,
        proxyClassInternalName,
        null,
        "java/lang/Object",
        null);
  }
}
