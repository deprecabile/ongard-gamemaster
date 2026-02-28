package com.ongard.game.authentication.client;

import com.ongard.game.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BffClientTest {

  private RestClient restClient;
  private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  private RestClient.RequestBodySpec requestBodySpec;
  private RestClient.ResponseSpec responseSpec;

  private BffClient bffClient;

  @BeforeEach
  void setUp() throws Exception {
    restClient = mock(RestClient.class);
    requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class, RETURNS_SELF);
    requestBodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
    responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

    bffClient = new BffClient("http://localhost:9999");
    Field field = BffClient.class.getDeclaredField("restClient");
    field.setAccessible(true);
    field.set(bffClient, restClient);
  }

  @Test
  void createUser_callsBffSuccessfully() {
    UUID userHash = UUID.randomUUID();

    bffClient.createUser(userHash, "player1");

    verify(restClient).post();
    verify(requestBodyUriSpec).uri(eq("/api/user"), any(Object[].class));
    verify(responseSpec).toBodilessEntity();
  }

  @Test
  void createUser_doesNotThrowOnSuccess() {
    assertThatCode(() -> bffClient.createUser(UUID.randomUUID(), "player1"))
        .doesNotThrowAnyException();
  }

  @Test
  void createUser_wrapsRestClientExceptionInAppException() {
    when(requestBodySpec.retrieve()).thenThrow(new RestClientException("Connection refused"));

    assertThatThrownBy(() -> bffClient.createUser(UUID.randomUUID(), "player1"))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Failed to sync user with game service");
  }

  @Test
  void createUser_wrapsAnyRuntimeExceptionInAppException() {
    when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("Unexpected error"));

    assertThatThrownBy(() -> bffClient.createUser(UUID.randomUUID(), "player1"))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Failed to sync user with game service");
  }
}
