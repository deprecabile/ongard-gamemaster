package com.ondgard.game.mail.service;

import com.ondgard.game.contract.mail.SendMailRequest;
import com.ondgard.game.mail.client.ResendClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendMailService implements MailService {

  private final ResendClient resendClient;

  @Override
  public void sendEmail(SendMailRequest request) {
    try{
      resendClient.sendEmail(request.to(), request.subject(), request.htmlBody());
      log.info("Email sent to {}", request.to());
    }catch(Exception e){
      log.error("Failed to send email to {}: {}", request.to(), e.getMessage(), e);
    }
  }

}
