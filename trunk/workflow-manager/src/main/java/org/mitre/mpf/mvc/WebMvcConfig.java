package org.mitre.mpf.mvc;

import java.util.List;

import org.mitre.mpf.wfm.util.ObjectMapperFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.smile.MappingJackson2SmileHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EnableWebMvc
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(
            // Remove preexisting Jackson so we can register our custom version.
            c -> c instanceof MappingJackson2HttpMessageConverter
                        // Prevent HTTP responses from using the Smile format.
                        || c instanceof MappingJackson2SmileHttpMessageConverter);

        var converter = new MappingJackson2HttpMessageConverter(
                ObjectMapperFactory.customObjectMapper());
        converters.add(converter);
    }
}
