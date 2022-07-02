package example;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

@ApplicationScoped
public class TestFunction {
  private final GreetingService greetingService;

  @Inject
  public TestFunction(GreetingService greetingService) {
    this.greetingService = greetingService;
  }

  @FunctionName("HttpExample")
  public HttpResponseMessage run(
      @HttpTrigger(
              name = "req",
              methods = {HttpMethod.GET, HttpMethod.POST},
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      final ExecutionContext context,
      int test,
      boolean[] test2) {

    String name = request.getQueryParameters().get("name");
    String greeting = greetingService.getGreeting(name);
    return request.createResponseBuilder(HttpStatus.OK).body(greeting).build();
  }
}
