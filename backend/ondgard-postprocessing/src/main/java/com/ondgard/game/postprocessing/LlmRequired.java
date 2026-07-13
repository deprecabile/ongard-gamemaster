package com.ondgard.game.postprocessing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as required in the generated Gemini LLM response schema.
 * Unlike {@code @JsonProperty(required = true)}, this annotation has no
 * runtime effect on Jackson serialization/deserialization.
 */
@Target( ElementType.FIELD )
@Retention( RetentionPolicy.SOURCE )
public @interface LlmRequired {
}
