package com.ondgard.game.chat.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;

/**
 * Mutability plan that deep-copies via Jackson (JSON serialize → deserialize)
 * instead of Java serialization. This avoids {@link java.io.NotSerializableException}
 * for objects containing non-serializable fields (e.g. lambda comparators inside TreeSet).
 */
class JsonMutabilityPlan extends MutableMutabilityPlan<Object> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  protected Object deepCopyNotNull(Object value) {
    try{
      byte[] json = MAPPER.writeValueAsBytes(value);
      return MAPPER.readValue(json, value.getClass());
    }catch(Exception e){
      throw new HibernateException("JSON deep-copy failed for " + value.getClass().getName(), e);
    }
  }
}
