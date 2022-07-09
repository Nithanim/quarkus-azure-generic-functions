package me.nithanim.quarkus.azure.generic.function.extension.deployment.builditems;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class FunctionClassResultBuildItem extends MultiBuildItem {
  DotName className;
  List<String> methodName;
  byte[] classFile;
}
