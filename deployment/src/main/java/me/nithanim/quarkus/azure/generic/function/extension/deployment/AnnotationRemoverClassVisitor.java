package me.nithanim.quarkus.azure.generic.function.extension.deployment;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import io.quarkus.gizmo.Gizmo;

class AnnotationRemoverClassVisitor extends ClassVisitor {
  public AnnotationRemoverClassVisitor(ClassVisitor outputClassVisitor) {
    super(Gizmo.ASM_API_VERSION, outputClassVisitor);
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    return new MethodVisitor(
        Gizmo.ASM_API_VERSION, super.visitMethod(access, name, descriptor, signature, exceptions)) {
      @Override
      public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (descriptor.equals("Lcom/microsoft/azure/functions/annotation/FunctionName;")) {
          return null;
        } else {
          return super.visitAnnotation(descriptor, visible);
        }
      }
    };
  }
}
