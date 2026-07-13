package com.ondgard.game.contract.mail;

public record SendMailRequest(
    String to,
    String subject,
    String htmlBody
) {}
