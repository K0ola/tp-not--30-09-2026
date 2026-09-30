package com.fitconnect.classservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/**
 * Serialise les {@code Page} sous une forme stable ({@code content} + {@code page})
 * au lieu de la structure interne de PageImpl.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class WebConfig {
}
