package me.nithanim.quarkus.azure.generic.function.extension.deployment.builditems;

import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.MultiBuildItem;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class FunctionClassRequestBuildItem extends MultiBuildItem {
  ClassInfo classInfo;
  List<MethodInfo> methodInfos;
}
