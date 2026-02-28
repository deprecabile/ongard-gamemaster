package com.ongard.game.authentication.contract;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest implements Serializable {

  @NotBlank
  private String username;

  @NotBlank
  private String refreshToken;
}
