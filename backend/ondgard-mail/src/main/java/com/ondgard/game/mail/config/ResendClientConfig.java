package com.ondgard.game.mail.config;

import com.ondgard.game.mail.client.ResendClient;
import com.resend.Resend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResendClientConfig {

  @Bean
  public Resend resend(@Value( "${resend.api.key}" ) String apiKey) {
    return new Resend(apiKey);
  }

  @Bean
  public ResendClient resendClient(Resend resend, @Value( "${resend.from}" ) String from) {
    return new ResendClient(resend, from);
  }

}
