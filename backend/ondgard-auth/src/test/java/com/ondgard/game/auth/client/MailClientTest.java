package com.ondgard.game.auth.client;

import com.ondgard.game.contract.mail.SendMailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MailClientTest {

  private RestClient restClient;
  private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  private RestClient.RequestBodySpec requestBodySpec;
  private RestClient.ResponseSpec responseSpec;

  private MailClient mailClient;

  private static final SendMailRequest REQUEST = new SendMailRequest("user@test.com", "Welcome", "<h1>Hello</h1>");

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

    mailClient = new MailClient("http://localhost:9999");
    Field field = MailClient.class.getDeclaredField("restClient");
    field.setAccessible(true);
    field.set(mailClient, restClient);
  }

  @Test
  void sendEmail_callsMailServiceSuccessfully() {
    mailClient.sendEmail(REQUEST);

    verify(restClient).post();
    verify(requestBodyUriSpec).uri(eq("/api/mail/send"), any(Object[].class));
    verify(responseSpec).toBodilessEntity();
  }

  @Test
  void sendEmail_doesNotThrowOnSuccess() {
    assertThatCode(() -> mailClient.sendEmail(REQUEST)).doesNotThrowAnyException();
  }

  @Test
  void sendEmail_doesNotThrowOnRestClientException() {
    when(requestBodySpec.retrieve()).thenThrow(new RestClientException("Connection refused"));

    assertThatCode(() -> mailClient.sendEmail(REQUEST)).doesNotThrowAnyException();
  }

  @Test
  void sendEmail_doesNotThrowOnAnyRuntimeException() {
    when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("Unexpected error"));

    assertThatCode(() -> mailClient.sendEmail(REQUEST)).doesNotThrowAnyException();
  }
}
