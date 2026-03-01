package com.ongard.game.warmup.runner;

import com.ongard.game.warmup.client.OllamaHealthClient;
import com.ongard.game.warmup.config.WarmupProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class WarmupRunnerRedisTest {

  @SuppressWarnings( "resource" )
  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:8-alpine")
          .withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  @BeforeAll
  static void setUpRedis() {
    connectionFactory = new LettuceConnectionFactory(
        redis.getHost(), redis.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
  }

  @AfterAll
  static void tearDownRedis() {
    if( connectionFactory != null ){
      connectionFactory.destroy();
    }
  }

  @Test
  void run_setsRedisKey_whenAllPhasesSucceed() throws Exception {
    OllamaHealthClient ollamaClient = mock(OllamaHealthClient.class);
    when(ollamaClient.isReady(anyString())).thenReturn(true);

    WarmupRunner runner = new WarmupRunner(buildTestProperties(), ollamaClient, redisTemplate);
    runner.run();

    String value = redisTemplate.opsForValue().get("ondgard:ollama:ready");
    assertThat(value).isEqualTo("ok");
  }

  @Test
  void run_keyPersistsInRedis_afterMultipleRuns() throws Exception {
    OllamaHealthClient ollamaClient = mock(OllamaHealthClient.class);
    when(ollamaClient.isReady(anyString())).thenReturn(true);

    WarmupRunner runner = new WarmupRunner(buildTestProperties(), ollamaClient, redisTemplate);
    runner.run();
    runner.run();

    String value = redisTemplate.opsForValue().get("ondgard:ollama:ready");
    assertThat(value).isEqualTo("ok");
  }

  private WarmupProperties buildTestProperties() {
    var gpu = new WarmupProperties.OllamaInstanceConfig();
    gpu.setBaseUrl("http://gpu:11434");
    gpu.setModel("gpu-model");

    var cpu = new WarmupProperties.OllamaInstanceConfig();
    cpu.setBaseUrl("http://cpu:11434");
    cpu.setModel("cpu-model");
    cpu.setEmbeddingModel("cpu-embed");

    var healthCheck = new WarmupProperties.HealthCheckConfig();
    healthCheck.setMaxRetries(3);
    healthCheck.setRetryIntervalMs(1);

    var warmup = new WarmupProperties.WarmupConfig();
    warmup.setTimeoutSeconds(10);
    warmup.setMaxRetries(2);

    var props = new WarmupProperties();
    props.setOllamaGpu(gpu);
    props.setOllamaCpu(cpu);
    props.setHealthCheck(healthCheck);
    props.setWarmup(warmup);
    return props;
  }
}
