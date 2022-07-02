package example;

import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
class HttpResponseMessageImpl implements HttpResponseMessage {
  HttpStatusType status;
  int statusCode;
  Map<String, String> headers;
  Object body;

  @Override
  public String getHeader(String key) {
    return headers.get(key);
  }

  static class HttpResponseMessageBuilderImpl implements HttpResponseMessage.Builder {
    HttpStatusType status;
    int statusCode;
    Map<String, String> headers = new HashMap<>();
    Object body;

    @Override
    public Builder status(HttpStatusType status) {
      this.status = status;
      return this;
    }

    @Override
    public Builder header(String key, String value) {
      this.headers.put(key, value);
      return this;
    }

    @Override
    public Builder body(Object body) {
      this.body = body;
      return this;
    }

    @Override
    public HttpResponseMessage build() {
      return new HttpResponseMessageImpl(status, statusCode, headers, body);
    }
  }
}
