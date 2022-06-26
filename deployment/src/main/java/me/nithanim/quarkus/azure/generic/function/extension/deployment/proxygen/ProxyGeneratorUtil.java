package me.nithanim.quarkus.azure.generic.function.extension.deployment.proxygen;

import java.util.List;

import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.jandex.VoidType;

public class ProxyGeneratorUtil {
  public static String[] getExceptions(MethodInfo methodInfo) {
    List<Type> prev = methodInfo.exceptions();
    String[] next = new String[prev.size()];
    for (int i = 0; i < next.length; i++) {
      next[i] = dotNameToType(prev.get(i).asClassType().name()).getInternalName();
    }
    return next;
  }

  static String getDescriptor(MethodInfo methodInfo) {
    StringBuilder sb = new StringBuilder();
    sb.append('(');
    int size = methodInfo.parameters().size();
    for (int i = 0; i < size; i++) {
      Type parameterType = methodInfo.parameters().get(i);
      sb.append(convertType(parameterType).getDescriptor());
    }
    sb.append(')');
    sb.append(convertType(methodInfo.returnType()).getDescriptor());
    return sb.toString();
  }

  static org.objectweb.asm.Type dotNameToType(DotName dotName) {
    return org.objectweb.asm.Type.getObjectType(getInternalName(dotName));
  }

  public static String getInternalName(DotName dotName) {
    return dotName.toString('/');
  }

  public static String getInternalName(Class<?> clazz) {
    return clazz.getName().replace('.', '/');
  }

  static org.objectweb.asm.Type convertType(Type type) {
    if (type instanceof ClassType) {
      return dotNameToType(type.name());
    } else if (type instanceof PrimitiveType) {
      return org.objectweb.asm.Type.getType(
          String.valueOf(primitiveDescriptor(type.asPrimitiveType())));
    } else if (type instanceof ArrayType) {
      return org.objectweb.asm.Type.getType(
          "[" + convertType(type.asArrayType().component()).getDescriptor());
    } else if (type instanceof VoidType) {
      return org.objectweb.asm.Type.VOID_TYPE;
    } else if (type instanceof ParameterizedType) {
      return dotNameToType(type.asParameterizedType().name());
    } else {
      throw new IllegalStateException("Unknown type " + type);
    }
  }

  static char primitiveDescriptor(PrimitiveType primitiveType) {
    switch (primitiveType.primitive()) {
      case BYTE:
        return 'B';
      case CHAR:
        return 'C';
      case DOUBLE:
        return 'D';
      case FLOAT:
        return 'F';
      case INT:
        return 'I';
      case LONG:
        return 'J';
      case SHORT:
        return 'S';
      case BOOLEAN:
        return 'Z';
      default:
        throw new IllegalStateException();
    }
  }
}
