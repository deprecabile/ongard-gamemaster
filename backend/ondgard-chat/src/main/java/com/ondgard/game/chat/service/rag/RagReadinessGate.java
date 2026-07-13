package com.ondgard.game.chat.service.rag;

import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

@Component
public class RagReadinessGate {

  private final CountDownLatch latch = new CountDownLatch(1);

  public void markReady() {
    latch.countDown();
  }

  public boolean isReady() {
    return latch.getCount() == 0;
  }
}
