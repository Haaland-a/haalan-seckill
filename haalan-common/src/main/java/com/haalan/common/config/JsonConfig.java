package com.haalan.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
@ConditionalOnClass(ObjectMapper.class)
public class JsonConfig {
	private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

	@Bean
	public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
		return jacksonObjectMapperBuilder -> {
			// long -> string
			jacksonObjectMapperBuilder.serializerByType(Long.class, ToStringSerializer.instance);
			jacksonObjectMapperBuilder.serializerByType(BigInteger.class, ToStringSerializer.instance);

			DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
			//序列化
			jacksonObjectMapperBuilder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
				@Override
				public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
					gen.writeString(value.format(formatter));
				}
			});
			//反序列化
			jacksonObjectMapperBuilder.deserializerByType(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
				@Override
				public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
					return LocalDateTime.parse(p.getValueAsString(), formatter);
				}
			});
		};
	}
}
