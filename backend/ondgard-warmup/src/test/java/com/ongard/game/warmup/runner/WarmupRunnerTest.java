package com.ongard.game.warmup.runner;

import com.ongard.game.warmup.client.OllamaHealthClient;
import com.ongard.game.warmup.client.OllamaHealthClient.WarmupException;
import com.ongard.game.warmup.config.WarmupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class WarmupRunnerTest {

  private static final String GPU_URL = "http://gpu:11434";
  private static final String CPU_URL = "http://cpu:11434";
  private static final String GPU_MODEL = "gpu-model";
  private static final String CPU_MODEL = "cpu-model";
  private static final String CPU_EMBED = "cpu-embed";
  private static final int TIMEOUT = 10;

  @Mock
  private OllamaHealthClient ollamaClient;
  @Mock
  private StringRedisTemplate redisTemplate;
  @Mock
  private ValueOperations<String, String> valueOps;

  private WarmupRunner runner;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    runner = new WarmupRunner(buildTestProperties(), ollamaClient, redisTemplate);
  }

  @Test
  void run_completesAllPhases_whenEverythingSucceeds() throws Exception {
    when(ollamaClient.isReady(anyString())).thenReturn(true);

    runner.run();

    verify(ollamaClient).isReady(GPU_URL);
    verify(ollamaClient).isReady(CPU_URL);
    verify(ollamaClient).warmUpGenerate(GPU_URL, GPU_MODEL, TIMEOUT);
    verify(ollamaClient).warmUpGenerate(CPU_URL, CPU_MODEL, TIMEOUT);
    verify(ollamaClient).warmUpEmbed(CPU_URL, CPU_EMBED, TIMEOUT);
    verify(valueOps).set("ondgard:ollama:ready", "ok");
  }

  @Test
  void run_retriesHealthCheck_untilInstanceBecomesReady() throws Exception {
    when(ollamaClient.isReady(GPU_URL))
        .thenReturn(false)
        .thenReturn(true);
    when(ollamaClient.isReady(CPU_URL))
        .thenReturn(true);

    runner.run();

    verify(ollamaClient, times(2)).isReady(GPU_URL);
    verify(valueOps).set("ondgard:ollama:ready", "ok");
  }

  @Test
  void run_retriesWarmup_onTransientFailure() throws Exception {
    when(ollamaClient.isReady(anyString())).thenReturn(true);

    doThrow(new WarmupException("timeout"))
        .doNothing()
        .when(ollamaClient).warmUpGenerate(GPU_URL, GPU_MODEL, TIMEOUT);

    runner.run();

    verify(ollamaClient, times(2)).warmUpGenerate(GPU_URL, GPU_MODEL, TIMEOUT);
    verify(valueOps).set("ondgard:ollama:ready", "ok");
  }

  @Test
  void run_warmsUpCpuModelsSequentially() throws Exception {
    when(ollamaClient.isReady(anyString())).thenReturn(true);

    runner.run();

    // Both CPU generate and embed should be called
    var inOrder = org.mockito.Mockito.inOrder(ollamaClient);
    inOrder.verify(ollamaClient).warmUpGenerate(CPU_URL, CPU_MODEL, TIMEOUT);
    inOrder.verify(ollamaClient).warmUpEmbed(CPU_URL, CPU_EMBED, TIMEOUT);
  }

  private WarmupProperties buildTestProperties() {
    var gpu = new WarmupProperties.OllamaInstanceConfig();
    gpu.setBaseUrl(GPU_URL);
    gpu.setModel(GPU_MODEL);

    var cpu = new WarmupProperties.OllamaInstanceConfig();
    cpu.setBaseUrl(CPU_URL);
    cpu.setModel(CPU_MODEL);
    cpu.setEmbeddingModel(CPU_EMBED);

    var healthCheck = new WarmupProperties.HealthCheckConfig();
    healthCheck.setMaxRetries(3);
    healthCheck.setRetryIntervalMs(1); // 1ms for fast tests

    var warmup = new WarmupProperties.WarmupConfig();
    warmup.setTimeoutSeconds(TIMEOUT);
    warmup.setMaxRetries(2);

    var props = new WarmupProperties();
    props.setOllamaGpu(gpu);
    props.setOllamaCpu(cpu);
    props.setHealthCheck(healthCheck);
    props.setWarmup(warmup);
    return props;
  }
}
