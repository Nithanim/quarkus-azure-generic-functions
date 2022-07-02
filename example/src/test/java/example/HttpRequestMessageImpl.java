package example;

import java.net.URI;
import java.util.Map;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;

import lombok.Value;

@Value
public class HttpRequestMessageImpl<T> implements HttpRequestMessage<T> {
  URI uri;
  HttpMethod httpMethod;
  Map<String, String> headers;
  Map<String, String> queryParameters;
  T body;

  @Override
  public HttpResponseMessage.Builder createResponseBuilder(HttpStatus httpStatus) {
    return new HttpResponseMessageImpl.HttpResponseMessageBuilderImpl().status(httpStatus);
  }

  @Override
  public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType httpStatusType) {
    return new HttpResponseMessageImpl.HttpResponseMessageBuilderImpl().status(httpStatusType);
  }
}
