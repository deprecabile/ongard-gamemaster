package com.ondgard.game.chat.config;

import com.ondgard.game.chat.model.CampaignContext;
import com.ondgard.game.chat.model.setup.CampaignSetupSession;
import com.ondgard.game.chat.session.SessionExpirationListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties( {SessionProperties.class, SetupSessionProperties.class} )
public class RedisConfig {

  @Bean
  public RedisTemplate<String, CampaignContext> campaignContextRedisTemplate(
      RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
    RedisTemplate<String, CampaignContext> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());

    JacksonJsonRedisSerializer<CampaignContext> serializer =
        new JacksonJsonRedisSerializer<>(objectMapper, CampaignContext.class);
    template.setValueSerializer(serializer);
    template.setHashValueSerializer(serializer);

    return template;
  }

  @Bean
  public RedisTemplate<String, CampaignSetupSession> setupSessionRedisTemplate(
      RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
    RedisTemplate<String, CampaignSetupSession> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());

    JacksonJsonRedisSerializer<CampaignSetupSession> serializer =
        new JacksonJsonRedisSerializer<>(objectMapper, CampaignSetupSession.class);
    template.setValueSerializer(serializer);
    template.setHashValueSerializer(serializer);

    return template;
  }

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory, SessionExpirationListener expirationListener) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(expirationListener, new PatternTopic("__keyevent@0__:expired"));
    return container;
  }

}
