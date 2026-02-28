package com.ongard.game.authentication.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterResponse implements Serializable {

  private UUID userHash;
  private String username;
  private String email;
  private LocalDateTime created;
}
