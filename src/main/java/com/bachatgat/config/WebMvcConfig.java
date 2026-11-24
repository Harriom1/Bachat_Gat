package com.bachatgat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Release-scoped URLs can be reused across navigations without an HTTP
        // round trip. Publish a new URL prefix when any of these assets change.
        for (String directory : new String[]{"js", "css", "i18n", "fonts"}) {
            registry.addResourceHandler("/assets/perf-v1/" + directory + "/**")
                    .addResourceLocations("classpath:/static/" + directory + "/")
                    .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        }

        // Static assets keep the same content until the next application build.
        // Reusing them avoids downloading CSS, JavaScript, translations, and
        // icons again on every device visit. HTML remains revalidated below so
        // deployments are discovered immediately.
        // A resource handler removes the matched path prefix before resolving
        // the file. Keep each URL prefix paired with its actual static folder;
        // otherwise /css/style.css is incorrectly looked up as
        // classpath:/static/style.css and returns a 404.
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .setUseLastModified(true);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .setUseLastModified(true);

        registry.addResourceHandler("/i18n/**")
                .addResourceLocations("classpath:/static/i18n/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .setUseLastModified(true);

        registry.addResourceHandler("/images/**", "/fonts/**")
                .addResourceLocations("classpath:/static/images/", "classpath:/static/fonts/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .setUseLastModified(true);

        registry.addResourceHandler(
                        "/*.svg", "/*.ico", "/*.png", "/*.jpg", "/*.jpeg", "/*.webp",
                        "/*.woff", "/*.woff2", "/*.ttf")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .setUseLastModified(true);

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache().mustRevalidate())
                .setUseLastModified(true);
    }
}
