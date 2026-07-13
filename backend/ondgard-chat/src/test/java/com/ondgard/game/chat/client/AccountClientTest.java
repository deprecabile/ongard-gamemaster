package com.ondgard.game.chat.client;

import com.ondgard.game.contract.account.CheckLimitResponse;
import com.ondgard.game.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountClientTest {

  private RestClient restClient;
  private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  private RestClient.RequestBodySpec requestBodySpec;
  private RestClient.ResponseSpec responseSpec;
  private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

  private AccountClient accountClient;

  @SuppressWarnings( "unchecked" )
  @BeforeEach
  void setUp() throws Exception {
    restClient = mock(RestClient.class);

    // POST chain
    requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class, RETURNS_SELF);
    requestBodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
    responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

    // GET chain — doReturn to avoid wildcard capture issues
    requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class, RETURNS_SELF);
    doReturn(requestHeadersUriSpec).when(restClient).get();
    when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(CheckLimitResponse.class)).thenReturn(new CheckLimitResponse(false, null));

    // Inject mock via reflection
    accountClient = new AccountClient("http://localhost:9999");
    Field field = AccountClient.class.getDeclaredField("restClient");
    field.setAccessible(true);
    field.set(accountClient, restClient);
  }

  @Test
  void initUserLimits_callsAccountServiceSuccessfully() {
    accountClient.initUserLimits("user-hash-1");

    verify(restClient).post();
    verify(requestBodyUriSpec).uri(eq("/api/internal/user/init"), any(Object[].class));
    verify(responseSpec).toBodilessEntity();
  }

  @Test
  void initUserLimits_wrapsExceptionInAppException() {
    when(requestBodySpec.retrieve()).thenThrow(new RestClientException("Connection refused"));

    assertThatThrownBy(() -> accountClient.initUserLimits("user-hash-1"))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Failed to init user limits on account service");
  }

  @Test
  void initCampaignUsage_callsAccountServiceSuccessfully() {
    accountClient.initCampaignUsage("char-hash-1", "user-hash-1");

    verify(restClient).post();
    verify(requestBodyUriSpec).uri(eq("/api/internal/campaign/init"), any(Object[].class));
    verify(responseSpec).toBodilessEntity();
  }

  @Test
  void initCampaignUsage_wrapsExceptionInAppException() {
    when(requestBodySpec.retrieve()).thenThrow(new RestClientException("Connection refused"));

    assertThatThrownBy(() -> accountClient.initCampaignUsage("char-hash-1", "user-hash-1"))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Failed to init campaign usage on account service");
  }

  @Test
  void addTokensAsync_callsAccountService() {
    accountClient.addTokensAsync("char-hash-1", 500L);

    verify(restClient).post();
    verify(requestBodyUriSpec).uri(eq("/api/internal/tokens"), any(Object[].class));
    verify(responseSpec).toBodilessEntity();
  }

  @Test
  void addTokensAsync_logsErrorAndSwallows() {
    when(requestBodySpec.retrieve()).thenThrow(new RestClientException("Connection refused"));

    assertThatCode(() -> accountClient.addTokensAsync("char-hash-1", 500L))
        .doesNotThrowAnyException();
  }

  @Test
  void checkLimit_returnsResponse() {
    var expected = new CheckLimitResponse(true, "MONTHLY");
    when(responseSpec.body(CheckLimitResponse.class)).thenReturn(expected);

    CheckLimitResponse result = accountClient.checkLimit("user-hash-1");

    assertThat(result).isEqualTo(expected);
    verify(restClient).get();
    verify(requestHeadersUriSpec).retrieve();
  }

  @Test
  void checkLimit_wrapsExceptionInAppException() {
    when(requestHeadersUriSpec.retrieve()).thenThrow(new RestClientException("Connection refused"));

    assertThatThrownBy(() -> accountClient.checkLimit("user-hash-1"))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Failed to check token limit on account service");
  }
}
