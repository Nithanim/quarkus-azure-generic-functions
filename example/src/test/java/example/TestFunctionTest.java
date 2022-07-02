package example;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpResponseMessage;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class TestFunctionTest {

  @Test
  void test() throws Exception {
    TestFunction.class.getClassLoader();
    Class<?> proxyClass =
        Class.forName(
            TestFunction.class.getName() + "QuarkusEntryProxy",
            true,
            TestFunction.class.getClassLoader());

    Method originalMethod =
        Arrays.stream(TestFunction.class.getDeclaredMethods())
            .filter(m -> m.getName().equals("run"))
            .findAny()
            .orElseThrow();
    Method proxyMethod =
        proxyClass.getMethod(originalMethod.getName(), originalMethod.getParameterTypes());

    var request =
        new HttpRequestMessageImpl<Optional<String>>(
            null, HttpMethod.GET, Map.of(), Map.of("name", "test"), Optional.empty());

    Object proxy = proxyClass.getDeclaredConstructor().newInstance();
    Object rawResult = proxyMethod.invoke(proxy, request, null, 42, new boolean[2]);
    HttpResponseMessage result = (HttpResponseMessage) rawResult;

    Assertions.assertThat(result.getBody()).isEqualTo("Hello, test!");
  }
}
