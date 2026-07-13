package com.ondgard.game.auth.contract.google;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthRequest implements Serializable {

  @NotBlank
  private String credential;

  @Pattern( regexp = "^[a-zA-Z0-9_-]+$" )
  @Size( min = 3, max = 30 )
  private String username;
}
