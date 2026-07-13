package com.ondgard.game.auth.client;

import com.ondgard.game.contract.mail.SendMailRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class MailClient {

  private final RestClient restClient;

  public MailClient(@Value( "${ondgard.mail.url}" ) String mailUrl) {
    this.restClient = RestClient.builder().baseUrl(mailUrl).build();
  }

  public void sendEmail(SendMailRequest request) {
    log.debug("Sending email to={} subject={}", request.to(), request.subject());
    try{
      restClient.post().uri("/api/mail/send").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().toBodilessEntity();
      log.debug("Email sent successfully to={}", request.to());
    }catch(Exception e){
      log.error("Failed to send email to={}", request.to(), e);
    }
  }
}
