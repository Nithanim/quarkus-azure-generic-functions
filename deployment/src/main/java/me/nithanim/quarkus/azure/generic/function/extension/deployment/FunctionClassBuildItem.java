package me.nithanim.quarkus.azure.generic.function.extension.deployment;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

public final class FunctionClassBuildItem extends MultiBuildItem {
  private final DotName className;
  private final List<String> methodName;
  private final byte[] classFile;

  public FunctionClassBuildItem(DotName className, List<String> methodName, byte[] classFile) {
    this.className = className;
    this.methodName = methodName;
    this.classFile = classFile;
  }

  public DotName getClassName() {
    return className;
  }

  public List<String> getMethodName() {
    return methodName;
  }

  public byte[] getClassFile() {
    return classFile;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FunctionClassBuildItem that = (FunctionClassBuildItem) o;
    return Objects.equals(className, that.className)
        && Objects.equals(methodName, that.methodName)
        && Arrays.equals(classFile, that.classFile);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(className, methodName);
    result = 31 * result + Arrays.hashCode(classFile);
    return result;
  }
}
