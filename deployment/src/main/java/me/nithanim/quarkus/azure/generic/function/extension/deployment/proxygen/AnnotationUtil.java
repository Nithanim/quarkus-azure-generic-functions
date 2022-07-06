package me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.MethodInfo;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import static me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen.ProxyGeneratorUtil.dotNameToType;

class AnnotationUtil {

  public static void visitAnnotations(MethodInfo methodInfo, MethodVisitor mv) {
    for (AnnotationInstance annotationInstance : methodInfo.annotations()) {
      if (annotationInstance.target().kind() == AnnotationTarget.Kind.METHOD) {
        AnnotationVisitor av =
            mv.visitAnnotation(dotNameToType(annotationInstance.name()).getDescriptor(), true);
        visitAnnotations(annotationInstance, av);
        av.visitEnd();
      } else if (annotationInstance.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
        AnnotationVisitor av =
            mv.visitParameterAnnotation(
                annotationInstance.target().asMethodParameter().position(),
                dotNameToType(annotationInstance.name()).getDescriptor(),
                true);
        visitAnnotations(annotationInstance, av);
        av.visitEnd();
      }
    }
  }

  public static void visitAnnotations(AnnotationInstance annotationInstance, AnnotationVisitor av) {
    for (AnnotationValue value : annotationInstance.values()) {
      visitAnnotationValue(av, value);
    }
  }

  public static void visitAnnotationValue(AnnotationVisitor av, AnnotationValue value) {
    switch (value.kind()) {
      case ENUM:
        av.visitEnum(
            value.name(), dotNameToType(value.asEnumType()).getDescriptor(), value.asEnum());
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
