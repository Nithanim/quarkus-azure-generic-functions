package me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen;

import java.util.List;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkus.deployment.QuarkusClassWriter;
import io.quarkus.gizmo.DescriptorUtils;
import me.nithanim.quarkus.azure.generic.function.extension.deployment.FunctionClassBuildItem;
import me.nithanim.quarkus.azure.genericfunction.FunctionBaseInterface;
import me.nithanim.quarkus.azure.genericfunction.FunctionBrain;

// https://asm.ow2.io/asm4-guide.pdf
public class ProxyGenerator {
  public FunctionClassBuildItem generateProxy(ClassInfo classInfo, List<MethodInfo> methodInfos) {
    DotName originalClassName = classInfo.name();
    DotName proxyClassName =
        DotName.createComponentized(
            originalClassName.prefix(), originalClassName.local() + "QuarkusEntryProxy");

    String proxyClassInternalName = proxyClassName.toString('/');
    ClassWriter cw = new QuarkusClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    ClassVisitor cv = cw; // new TraceClassVisitor(cw, new PrintWriter(System.out));

    cv.visit(
        Opcodes.V1_5,
        Opcodes.ACC_PUBLIC,
        proxyClassInternalName,
        null,
        "java/lang/Object",
        new String[] {ProxyGeneratorUtil.getInternalName(FunctionBaseInterface.class)});
    // TODO PLAN:
    // Make recorder together with proxy that sets the original handler as this delegate

    cv.visitField(
        Opcodes.ACC_PUBLIC,
        "delegate",
        ProxyGeneratorUtil.dotNameToType(originalClassName).getDescriptor(),
        null,
        null);

    for (MethodInfo methodInfo : methodInfos) {
      String descriptor = ProxyGeneratorUtil.getDescriptor(methodInfo);
      String[] exceptions = ProxyGeneratorUtil.getExceptions(methodInfo);
      MethodVisitor mv =
          cv.visitMethod(Opcodes.ACC_PUBLIC, methodInfo.name(), descriptor, null, exceptions);
      // mv.visitAnnotationDefault();
      // mv.visitAnnotation();
      // mv.visitParameterAnnotation();
      buildHandlerMethod(originalClassName, methodInfo, proxyClassInternalName, descriptor, mv);
      mv.visitMaxs(3, 3); // For some reason the automatic calculation does not work
      mv.visitEnd();
    }

    {
      String descriptor = "(Ljava/lang/Object;)V";
      MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "setDelegate", descriptor, null, null);
      buildSetter(originalClassName, proxyClassInternalName, mv);
      mv.visitMaxs(3, 3);
      mv.visitEnd();
    }
    {
      String descriptor = "()Ljava/lang/Class;";
      MethodVisitor mv =
          cv.visitMethod(Opcodes.ACC_PUBLIC, "getDelegateClass", descriptor, null, null);
      mv.visitCode();
      mv.visitLdcInsn(ProxyGeneratorUtil.dotNameToType(originalClassName));
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(Opcodes.RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }

    cv.visitEnd();
    return new FunctionClassBuildItem(
        proxyClassName,
        methodInfos.stream().map(MethodInfo::name).collect(Collectors.toList()),
        cw.toByteArray());
  }

  private void buildHandlerMethod(
      DotName originalClassName,
      MethodInfo methodInfo,
      String proxyClassInternalName,
      String descriptor,
      MethodVisitor mv) {

    for (AnnotationInstance annotationInstance : methodInfo.annotations()) {
      if (annotationInstance.target().kind() == AnnotationTarget.Kind.METHOD) {
        AnnotationVisitor av =
            mv.visitAnnotation(
                ProxyGeneratorUtil.dotNameToType(annotationInstance.name()).getDescriptor(), true);
        visitAnnotations(annotationInstance, av);
        av.visitEnd();
      } else if (annotationInstance.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
        AnnotationVisitor av =
            mv.visitParameterAnnotation(
                annotationInstance.target().asMethodParameter().position(),
                ProxyGeneratorUtil.dotNameToType(annotationInstance.name()).getDescriptor(),
                true);
        visitAnnotations(annotationInstance, av);
        av.visitEnd();
      }
    }

    mv.visitCode();
    // mv.visitFrame();
    /*mv.visitMethodInsn(
    Opcodes.INVOKESTATIC,
    "me/nithanim/quarkus/azure/genericfunction/QuarkusBootstrap",
    "ensureBootstrapped",
    "()V",
    false);*/
    mv.visitIntInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.DUP);
    mv.visitFieldInsn(
        Opcodes.GETFIELD,
        proxyClassInternalName,
        "delegate",
        ProxyGeneratorUtil.dotNameToType(originalClassName).getDescriptor());
    mv.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        ProxyGeneratorUtil.getInternalName(FunctionBrain.class),
        "detour",
        "(L"
            + ProxyGeneratorUtil.getInternalName(FunctionBaseInterface.class)
            + ";Ljava/lang/Object;)V",
        false);

    mv.visitIntInsn(Opcodes.ALOAD, 0);
    mv.visitFieldInsn(
        Opcodes.GETFIELD,
        proxyClassInternalName,
        "delegate",
        ProxyGeneratorUtil.dotNameToType(originalClassName).getDescriptor());
    int p = 1;
    for (Type parameterType : methodInfo.parameters()) {
      var asmParameterType = ProxyGeneratorUtil.convertType(parameterType);
      mv.visitIntInsn(asmParameterType.getOpcode(Opcodes.ILOAD), p++);
    }
    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        ProxyGeneratorUtil.dotNameToType(originalClassName).getInternalName(),
        methodInfo.name(),
        descriptor,
        false);
    var asmReturnType = ProxyGeneratorUtil.convertType(methodInfo.returnType());
    mv.visitInsn(asmReturnType.getOpcode(Opcodes.IRETURN));
  }

  private void buildSetter(
      DotName originalClassName, String proxyClassInternalName, MethodVisitor mv) {

    mv.visitAnnotation("Ljava/lang/Override;", true).visitEnd();

    mv.visitCode();
    mv.visitIntInsn(Opcodes.ALOAD, 0);
    mv.visitIntInsn(Opcodes.ALOAD, 1);
    mv.visitTypeInsn(
        Opcodes.CHECKCAST, DescriptorUtils.objectToInternalClassName(originalClassName.toString()));
    mv.visitFieldInsn(
        Opcodes.PUTFIELD,
        proxyClassInternalName,
        "delegate",
        ProxyGeneratorUtil.dotNameToType(originalClassName).getDescriptor());

    mv.visitInsn(Opcodes.RETURN);
  }

  private void visitAnnotations(AnnotationInstance annotationInstance, AnnotationVisitor av) {
    for (AnnotationValue value : annotationInstance.values()) {
      visitAnnotationValue(av, value);
    }
  }

  private void visitAnnotationValue(AnnotationVisitor av, AnnotationValue value) {
    switch (value.kind()) {
      case ENUM:
        av.visitEnum(
            value.name(),
            ProxyGeneratorUtil.dotNameToType(value.asEnumType()).getDescriptor(),
            value.asEnum());
        break;
      case NESTED:
        var av2 = av.visitAnnotation(value.name(), null);
        System.out.println(av2);
        av2.visitEnd();
        break;
      case ARRAY:
        var av3 = av.visitArray(value.name());
        for (AnnotationValue annotationValue : (AnnotationValue[]) value.value()) {
          visitAnnotationValue(av3, annotationValue);
        }
        av3.visitEnd();
        break;
      case UNKNOWN:
        break;
      default:
        av.visit(value.name(), value.value());
    }
  }
}
