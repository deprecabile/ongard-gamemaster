package com.ondgard.game.warmup.runner;

import com.ondgard.game.warmup.client.OllamaHealthClient;
import com.ondgard.game.warmup.client.OllamaHealthClient.ModelNotFoundException;
import com.ondgard.game.warmup.client.OllamaHealthClient.WarmupException;
import com.ondgard.game.warmup.config.WarmupProperties;
import com.ondgard.game.warmup.config.WarmupProperties.OllamaInstanceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarmupRunner implements CommandLineRunner {

  private static final int EXIT_CODE_FAILURE = 1;

  private final WarmupProperties properties;
  private final OllamaHealthClient ollamaClient;
  private final StringRedisTemplate redisTemplate;

  private record Availability( boolean gpuReady, boolean cpuReady ) {
    boolean anyReady() {
      return gpuReady || cpuReady;
    }

    boolean noneReady() {
      return !gpuReady && !cpuReady;
    }
  }

  @Override
  public void run(String... args) {
    try{
      var available = phaseProbe();

      if( available.noneReady() ){
        log.info("No Ollama instances found — publishing RAG signal immediately and skipping warmup.");
        publishRagSignal();
      } else{
        phaseWarmUp(available);
      }

      phaseSignal();
      log.info("Warmup completed successfully. Exiting.");
    }catch(Exception e){
      log.error("Warmup failed: {}", e.getMessage());
      System.exit(EXIT_CODE_FAILURE);
    }
  }

  private Availability phaseProbe() {
    log.info("Phase 1 — Health check: probing Ollama instances...");

    var healthCheckConfig = properties.getHealthCheck();

    CompletableFuture<Boolean> gpuReady = CompletableFuture.supplyAsync(() ->
        probeInstance("ollama-gpu", properties.getOllamaGpu().getBaseUrl(), healthCheckConfig));

    CompletableFuture<Boolean> cpuReady = CompletableFuture.supplyAsync(() ->
        probeInstance("ollama-cpu", properties.getOllamaCpu().getBaseUrl(), healthCheckConfig));

    var result = new Availability(gpuReady.join(), cpuReady.join());

    if( result.gpuReady && result.cpuReady ){
      log.info("Phase 1 — All Ollama instances are ready.");
    } else{
      log.warn("Phase 1 — Ollama availability: gpu={}, cpu={}.",
          result.gpuReady ? "ready" : "unavailable", result.cpuReady ? "ready" : "unavailable");
    }

    return result;
  }

  private boolean probeInstance(String name, String baseUrl, WarmupProperties.HealthCheckConfig config) {
    for( int attempt = 1; attempt <= config.getMaxRetries(); attempt++ ){
      if( ollamaClient.isReady(baseUrl) ){
        log.info("{} is ready at {}", name, baseUrl);
        return true;
      }
      log.info("{} not ready, attempt {}/{} — retrying in {}ms...",
          name, attempt, config.getMaxRetries(), config.getRetryIntervalMs());
      try{
        Thread.sleep(config.getRetryIntervalMs());
      }catch(InterruptedException e){
        Thread.currentThread().interrupt();
        log.warn("{} health check interrupted", name);
        return false;
      }
    }
    log.warn("{} at {} not reachable after {} retries — skipping", name, baseUrl, config.getMaxRetries());
    return false;
  }

  private void phaseWarmUp(Availability available) {
    log.info("Phase 2 — Warming up models...");

    var warmupConfig = properties.getWarmup();
    OllamaInstanceConfig gpu = properties.getOllamaGpu();
    OllamaInstanceConfig cpu = properties.getOllamaCpu();

    CompletableFuture<Void> warmGpu = available.gpuReady
        ? CompletableFuture.runAsync(() ->
        warmUpSafe("GPU generate", gpu.getBaseUrl(), gpu.getModel(), warmupConfig, false))
        : CompletableFuture.completedFuture(null);

    CompletableFuture<Void> warmCpu = available.cpuReady
        ? CompletableFuture.runAsync(() -> {
      warmUpSafe("CPU generate", cpu.getBaseUrl(), cpu.getModel(), warmupConfig, false);
      warmUpSafe("CPU embed", cpu.getBaseUrl(), cpu.getEmbeddingModel(), warmupConfig, true);
    })
        : CompletableFuture.completedFuture(null);

    CompletableFuture.allOf(warmGpu, warmCpu).join();

    publishRagSignal();

    log.info("Phase 2 — Model warmup phase complete.");
  }

  private void warmUpSafe(String label, String baseUrl, String model,
                          WarmupProperties.WarmupConfig config, boolean isEmbed) {
    for( int attempt = 1; attempt <= config.getMaxRetries(); attempt++ ){
      try{
        log.info("{}: warming up model '{}' (attempt {}/{})", label, model, attempt, config.getMaxRetries());
        if( isEmbed ){
          ollamaClient.warmUpEmbed(baseUrl, model, config.getTimeoutSeconds());
        } else{
          ollamaClient.warmUpGenerate(baseUrl, model, config.getTimeoutSeconds());
        }
        log.info("{}: model '{}' warmed up successfully.", label, model);
        return;
      }catch(ModelNotFoundException e){
        log.warn("{}: model '{}' not found — skipping. {}", label, model, e.getMessage());
        return;
      }catch(WarmupException e){
        if( attempt == config.getMaxRetries() ){
          log.warn("{}: warmup failed after {} retries — skipping. Error: {}", label, config.getMaxRetries(), e.getMessage());
        } else{
          log.warn("{}: attempt {}/{} failed: {} — retrying...", label, attempt, config.getMaxRetries(), e.getMessage());
        }
      }
    }
  }

  private void publishRagSignal() {
    redisTemplate.convertAndSend("ondgard:rag:warmup:completed", "ok");
    log.info("RAG warmup signal published on topic 'ondgard:rag:warmup:completed'");
  }

  private void phaseSignal() {
    log.info("Phase 3 — Signaling readiness to Redis...");
    redisTemplate.opsForValue().set("ondgard:ollama:ready", "ok");
    log.info("Phase 3 — Redis key 'ondgard:ollama:ready' set.");
  }
}
