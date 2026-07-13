package com.ondgard.game.mail.client;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ResendClient {

  private final Resend resend;
  private final String from;

  public void sendEmail(String to, String subject, String htmlBody) throws ResendException {
    CreateEmailOptions request = CreateEmailOptions.builder()
        .from(from)
        .to(to)
        .subject(subject)
        .html(htmlBody)
        .build();

    CreateEmailResponse response = resend.emails().send(request);
    log.debug("Resend response: id={}", response.getId());
  }

}
