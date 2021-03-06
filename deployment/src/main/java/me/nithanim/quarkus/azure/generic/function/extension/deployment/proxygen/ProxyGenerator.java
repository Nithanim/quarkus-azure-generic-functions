package me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen;

import java.util.List;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkus.deployment.QuarkusClassWriter;
import io.quarkus.gizmo.DescriptorUtils;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.builditems.FunctionClassResultBuildItem;
import me.nithanim.quarkus.azure.genericfunction.FunctionBaseInterface;
import me.nithanim.quarkus.azure.genericfunction.FunctionBrain;

import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.AnnotationUtil.visitAnnotations;
import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil.convertType;
import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil.dotNameToType;
import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil.getDescriptor;
import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil.getExceptions;
import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil.getInternalName;

// https://asm.ow2.io/asm4-guide.pdf
public class ProxyGenerator {
  public FunctionClassResultBuildItem generateProxy(
      ClassInfo classInfo, List<MethodInfo> methodInfos) {
    DotName originalClassName = classInfo.name();
    DotName proxyClassName = makeProxyClassName(originalClassName);

    String proxyClassInternalName = getInternalName(proxyClassName);
    ClassWriter cw = new QuarkusClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    visitProxyClass(methodInfos, originalClassName, proxyClassInternalName, cw);
    return new FunctionClassResultBuildItem(
        proxyClassName,
        methodInfos.stream().map(MethodInfo::name).collect(Collectors.toList()),
        cw.toByteArray());
  }

  private void visitProxyClass(
      List<MethodInfo> methodInfos,
      DotName originalClassName,
      String proxyClassInternalName,
      ClassVisitor cv) {

    // cv = new TraceClassVisitor(cv, new PrintWriter(System.out));
    // alternatively, quarkus can also dump generated/transformed classes.
    // quarkus.debug.generated-classes-dir
    // quarkus.debug.transformed-classes-dir

    visitClassHeader(cv, proxyClassInternalName);

    visitDelegateField(cv, originalClassName);

    visitMethodConstructor(cv);
    visitMethodSetDelegate(cv, originalClassName, proxyClassInternalName);
    visitMethodGetDelegateClass(originalClassName, cv);

    for (MethodInfo methodInfo : methodInfos) {
      visitMethodEntry(cv, originalClassName, proxyClassInternalName, methodInfo);
    }

    cv.visitEnd();
  }

  private void visitMethodConstructor(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
    mv.visitEnd();
  }

  private void visitMethodSetDelegate(
      ClassVisitor cv, DotName originalClassName, String proxyClassInternalName) {
    String descriptor = "(Ljava/lang/Object;)V";
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "setDelegate", descriptor, null, null);

    mv.visitAnnotation("Ljava/lang/Override;", true).visitEnd();

    mv.visitCode();
    instructionThis(mv);
    mv.visitIntInsn(Opcodes.ALOAD, 1);
    mv.visitTypeInsn(
        Opcodes.CHECKCAST, DescriptorUtils.objectToInternalClassName(originalClassName.toString()));
    mv.visitFieldInsn(
        Opcodes.PUTFIELD,
        proxyClassInternalName,
        "delegate",
        dotNameToType(originalClassName).getDescriptor());

    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(-1, -1);
    mv.visitEnd();
  }

  private void visitMethodGetDelegateClass(DotName originalClassName, ClassVisitor cv) {
    String descriptor = "()Ljava/lang/Class;";
    MethodVisitor mv =
        cv.visitMethod(Opcodes.ACC_PUBLIC, "getDelegateClass", descriptor, null, null);
    mv.visitCode();
    mv.visitLdcInsn(dotNameToType(originalClassName));
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(-1, -1);
    mv.visitEnd();
  }

  private void visitMethodEntry(
      ClassVisitor cv,
      DotName originalClassName,
      String proxyClassInternalName,
      MethodInfo methodInfo) {

    String descriptor = getDescriptor(methodInfo);
    String[] exceptions = getExceptions(methodInfo);
    MethodVisitor mv =
        cv.visitMethod(Opcodes.ACC_PUBLIC, methodInfo.name(), descriptor, null, exceptions);
    buildHandlerMethod(originalClassName, methodInfo, proxyClassInternalName, descriptor, mv);
    mv.visitEnd();
  }

  private void visitDelegateField(ClassVisitor cv, DotName originalClassName) {
    cv.visitField(
        Opcodes.ACC_PUBLIC,
        "delegate",
        dotNameToType(originalClassName).getDescriptor(),
        null,
        null);
  }

  private DotName makeProxyClassName(DotName originalClassName) {
    return DotName.createComponentized(
        originalClassName.prefix(), originalClassName.local() + "QuarkusEntryProxy");
  }

  private void visitClassHeader(ClassVisitor cv, String proxyClassInternalName) {
    cv.visit(
        Opcodes.V1_5,
        Opcodes.ACC_PUBLIC,
        proxyClassInternalName,
        null,
        "java/lang/Object",
        new String[] {getInternalName(FunctionBaseInterface.class)});
  }

  private void buildHandlerMethod(
      DotName originalClassName,
      MethodInfo methodInfo,
      String proxyClassInternalName,
      String descriptor,
      MethodVisitor mv) {

    visitAnnotations(methodInfo, mv);

    mv.visitCode();
    instructionThis(mv);
    instructionLoadDelegate(mv, originalClassName, proxyClassInternalName);

    Label lbl = new Label();
    mv.visitJumpInsn(Opcodes.IFNONNULL, lbl);

    instructionThis(mv);
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        getInternalName(FunctionBrain.class),
        "detour",
        "(L" + getInternalName(FunctionBaseInterface.class) + ";)V",
        false);

    mv.visitLabel(lbl);

    instructionThis(mv);
    instructionLoadDelegate(mv, originalClassName, proxyClassInternalName);
    int p = 1;
    for (Type parameterType : methodInfo.parameters()) {
      var asmParameterType = convertType(parameterType);
      mv.visitIntInsn(asmParameterType.getOpcode(Opcodes.ILOAD), p++);
    }
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        dotNameToType(originalClassName).getInternalName(),
        methodInfo.name(),
        descriptor,
        false);
    var asmReturnType = convertType(methodInfo.returnType());
    mv.visitInsn(asmReturnType.getOpcode(Opcodes.IRETURN));
    mv.visitMaxs(-1, -1);
  }

  private void instructionLoadDelegate(
      MethodVisitor mv, DotName originalClassName, String proxyClassInternalName) {
    mv.visitFieldInsn(
        Opcodes.GETFIELD,
        proxyClassInternalName,
        "delegate",
        dotNameToType(originalClassName).getDescriptor());
  }

  private void instructionThis(MethodVisitor mv) {
    mv.visitIntInsn(Opcodes.ALOAD, 0);
  }
}
