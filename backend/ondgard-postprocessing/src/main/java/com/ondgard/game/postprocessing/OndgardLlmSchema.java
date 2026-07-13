package com.ondgard.game.postprocessing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target( ElementType.TYPE )
@Retention( RetentionPolicy.SOURCE )
public @interface OndgardLlmSchema {

  /**
   * Prefix for the generated constant name. E.g. "INVENTORY" produces {@code INVENTORY_SCHEMA}.
   */
  String value();
}
