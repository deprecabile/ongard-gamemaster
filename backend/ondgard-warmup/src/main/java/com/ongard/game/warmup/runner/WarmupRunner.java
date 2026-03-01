package com.ongard.game.warmup.runner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.ongard.game.warmup.client.OllamaHealthClient;
import com.ongard.game.warmup.client.OllamaHealthClient.ModelNotFoundException;
import com.ongard.game.warmup.client.OllamaHealthClient.WarmupException;
import com.ongard.game.warmup.config.WarmupProperties;
import com.ongard.game.warmup.config.WarmupProperties.OllamaInstanceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarmupRunner implements CommandLineRunner {

  private static final int EXIT_CODE_UNREACHABLE = 1;
  private static final int EXIT_CODE_MODEL_NOT_FOUND = 4;

  private final WarmupProperties properties;
  private final OllamaHealthClient ollamaClient;
  private final StringRedisTemplate redisTemplate;

  @Override
  public void run(String... args) {
    try{
      phaseHealthCheck();
      phaseWarmUp();
      phaseSignal();
      log.info("Warmup completed successfully. Exiting.");
    }catch(ModelNotFoundException e){
      log.error("Model not found: {}", e.getMessage());
      System.exit(EXIT_CODE_MODEL_NOT_FOUND);
    }catch(Exception e){
      log.error("Warmup failed: {}", e.getMessage());
      System.exit(EXIT_CODE_UNREACHABLE);
    }
  }

  private void phaseHealthCheck() {
    log.info("Phase 1 — Health check: waiting for Ollama instances...");

    var healthCheckConfig = properties.getHealthCheck();

    CompletableFuture<Void> gpuReady = CompletableFuture.runAsync(() ->
        waitForReady("ollama-gpu", properties.getOllamaGpu().getBaseUrl(), healthCheckConfig));

    CompletableFuture<Void> cpuReady = CompletableFuture.runAsync(() ->
        waitForReady("ollama-cpu", properties.getOllamaCpu().getBaseUrl(), healthCheckConfig));

    CompletableFuture.allOf(gpuReady, cpuReady).join();

    log.info("Phase 1 — All Ollama instances are ready.");
  }

  private void waitForReady(String name, String baseUrl, WarmupProperties.HealthCheckConfig config) {
    for( int attempt = 1; attempt <= config.getMaxRetries(); attempt++ ){
      if( ollamaClient.isReady(baseUrl) ){
        log.info("{} is ready at {}", name, baseUrl);
        return;
      }
      log.info("{} not ready, attempt {}/{} — retrying in {}ms...",
          name, attempt, config.getMaxRetries(), config.getRetryIntervalMs());
      try{
        Thread.sleep(config.getRetryIntervalMs());
      }catch(InterruptedException e){
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted waiting for " + name, e);
      }
    }
    throw new RuntimeException(
        "%s at %s not reachable after %d retries".formatted(name, baseUrl, config.getMaxRetries()));
  }

  private void phaseWarmUp() throws WarmupException {
    log.info("Phase 2 — Warming up models...");

    var warmupConfig = properties.getWarmup();
    OllamaInstanceConfig gpu = properties.getOllamaGpu();
    OllamaInstanceConfig cpu = properties.getOllamaCpu();

    CompletableFuture<Void> warmGpu = CompletableFuture.runAsync(() -> {
      try{
        warmUpWithRetry("GPU generate", gpu.getBaseUrl(), gpu.getModel(), warmupConfig, false);
      }catch(WarmupException e){
        throw new CompletionException(e);
      }
    });

    CompletableFuture<Void> warmCpuSequential = CompletableFuture.runAsync(() -> {
      try{
        warmUpWithRetry("CPU generate", cpu.getBaseUrl(), cpu.getModel(), warmupConfig, false);
        warmUpWithRetry("CPU embed", cpu.getBaseUrl(), cpu.getEmbeddingModel(), warmupConfig, true);
      }catch(WarmupException e){
        throw new CompletionException(e);
      }
    });

    try{
      CompletableFuture.allOf(warmGpu, warmCpuSequential).join();
    }catch(CompletionException e){
      Throwable cause = e.getCause();
      if( cause instanceof ModelNotFoundException mnf ){
        throw mnf;
      }
      if( cause instanceof WarmupException we ){
        throw we;
      }
      throw new RuntimeException("Warmup failed", cause);
    }

    log.info("Phase 2 — All models warmed up.");
  }

  private void warmUpWithRetry(String label, String baseUrl, String model,
                               WarmupProperties.WarmupConfig config, boolean isEmbed) throws WarmupException {
    for( int attempt = 1; attempt <= config.getMaxRetries(); attempt++ ){
      try{
        log.info("{}: warming up model '{}' (attempt {}/{})",
            label, model, attempt, config.getMaxRetries());
        if( isEmbed ){
          ollamaClient.warmUpEmbed(baseUrl, model, config.getTimeoutSeconds());
        } else{
          ollamaClient.warmUpGenerate(baseUrl, model, config.getTimeoutSeconds());
        }
        log.info("{}: model '{}' warmed up successfully.", label, model);
        return;
      }catch(ModelNotFoundException e){
        throw e;
      }catch(WarmupException e){
        if( attempt == config.getMaxRetries() ){
          throw e;
        }
        log.warn("{}: attempt {}/{} failed: {} — retrying...",
            label, attempt, config.getMaxRetries(), e.getMessage());
      }
    }
  }

  private void phaseSignal() {
    log.info("Phase 3 — Signaling readiness to Redis...");
    redisTemplate.opsForValue().set("ondgard:ollama:ready", "ok");
    log.info("Phase 3 — Redis key 'ondgard:ollama:ready' set to 'ok'.");
  }
}
