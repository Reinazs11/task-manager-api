package com.renan.taskmanager.common.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Stabilizes the JSON shape of paginated responses.
 *
 * <p><b>Why this config exists?</b>
 * By default, Spring Data serializes a {@code Page<T>} as a {@code PageImpl},
 * dumping pagination fields ({@code totalElements}, {@code pageable},
 * {@code sort}, {@code first}, {@code last}, {@code number}, ...) at the
 * top level alongside {@code content}. That format is an implementation
 * detail of {@code PageImpl} and Spring explicitly warns it has "no
 * guarantee about the stability of the resulting JSON structure".</p>
 *
 * <p>{@code VIA_DTO} switches serialization to Spring Data's {@code PagedModel}
 * shape, which is the documented, stable contract:
 * <pre>
 * {
 *   "content": [ ... ],
 *   "page": {
 *     "size": 20,
 *     "number": 0,
 *     "totalElements": 1,
 *     "totalPages": 1
 *   }
 * }
 * </pre>
 * Clients can now rely on the same envelope across Spring Boot versions.</p>
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {
}
