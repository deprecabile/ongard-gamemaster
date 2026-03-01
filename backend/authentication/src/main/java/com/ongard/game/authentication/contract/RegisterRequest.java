package com.ongard.game.authentication.contract;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest implements Serializable {

  @NotBlank
  @Size( min = 3, max = 30 )
  private String username;

  @NotBlank
  @Email
  private String email;

  @NotBlank
  @Size( min = 8, max = 100 )
  private String password;
}
