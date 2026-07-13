package com.ondgard.game.mail.controller;

import com.ondgard.game.contract.mail.SendMailRequest;
import com.ondgard.game.contract.mail.SendMailResponse;
import com.ondgard.game.mail.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping( "/api/mail" )
@RequiredArgsConstructor
public class MailController {

  private final MailService mailService;

  @PostMapping( "/send" )
  public ResponseEntity<SendMailResponse> sendEmail(@RequestBody SendMailRequest request) {
    Thread.ofVirtual().start(() -> mailService.sendEmail(request));
    return ResponseEntity.ok(new SendMailResponse(true));
  }

}
