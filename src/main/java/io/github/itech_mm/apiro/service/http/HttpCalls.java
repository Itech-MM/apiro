package io.github.itech_mm.apiro.service.http;

import io.github.itech_framework.api_client.annotations.methods.DELETE;
import io.github.itech_framework.api_client.annotations.methods.GET;
import io.github.itech_framework.api_client.annotations.methods.HEAD;
import io.github.itech_framework.api_client.annotations.methods.OPTIONS;
import io.github.itech_framework.api_client.annotations.methods.PATCH;
import io.github.itech_framework.api_client.annotations.methods.POST;
import io.github.itech_framework.api_client.annotations.methods.PUT;
import io.github.itech_framework.api_client.annotations.parameters.BaseUrl;
import io.github.itech_framework.api_client.annotations.parameters.Body;
import io.github.itech_framework.api_client.annotations.parameters.Headers;
import io.github.itech_framework.api_client.utils.CancelToken;
import io.github.itech_framework.api_client.utils.RequestOptions;
import io.github.itech_framework.api_client.utils.response.ApiExchange;
import org.apache.http.NameValuePair;

import java.util.List;
import io.github.itech_framework.api_client.annotations.ApiClient;

@ApiClient
public interface HttpCalls {

    @GET("")
    ApiExchange<byte[]> get(@BaseUrl String url, @Headers List<NameValuePair> headers,
                            @Body Object body, RequestOptions options, CancelToken token);

    @POST("")
    ApiExchange<byte[]> post(@BaseUrl String url, @Headers List<NameValuePair> headers,
                             @Body Object body, RequestOptions options, CancelToken token);

    @PUT("")
    ApiExchange<byte[]> put(@BaseUrl String url, @Headers List<NameValuePair> headers,
                            @Body Object body, RequestOptions options, CancelToken token);

    @PATCH("")
    ApiExchange<byte[]> patch(@BaseUrl String url, @Headers List<NameValuePair> headers,
                              @Body Object body, RequestOptions options, CancelToken token);

    @DELETE("")
    ApiExchange<byte[]> delete(@BaseUrl String url, @Headers List<NameValuePair> headers,
                               @Body Object body, RequestOptions options, CancelToken token);

    @OPTIONS("")
    ApiExchange<byte[]> options(@BaseUrl String url, @Headers List<NameValuePair> headers,
                                @Body Object body, RequestOptions options, CancelToken token);

    @HEAD("")
    ApiExchange<byte[]> head(@BaseUrl String url, @Headers List<NameValuePair> headers,
                             RequestOptions options, CancelToken token);
}