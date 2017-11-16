package org.mitre.mpf.mvc.util;


import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonNaming(JsonDropLeadingUnderscore.DropLeadingUnderscore.class)
public @interface JsonDropLeadingUnderscore {

	class DropLeadingUnderscore extends PropertyNamingStrategy {
		@Override
		public String nameForField(MapperConfig<?> config, AnnotatedField field, String defaultName) {
			return defaultName.charAt(0) == '_'
					? defaultName.substring(1)
					: defaultName;
		}
	}
}
