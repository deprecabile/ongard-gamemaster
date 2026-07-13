package com.ondgard.game.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TokenHashUtil {

  public static final String ALGORITHM = "SHA-256";

  private TokenHashUtil() {
  }

  public static String sha256Hex(String input) {
    try{
      MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    }catch(NoSuchAlgorithmException e){
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
