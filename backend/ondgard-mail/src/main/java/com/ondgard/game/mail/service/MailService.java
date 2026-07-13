package com.ondgard.game.mail.service;

import com.ondgard.game.contract.mail.SendMailRequest;

public interface MailService {

  void sendEmail(SendMailRequest request);

}
