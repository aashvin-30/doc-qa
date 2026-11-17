package com.docqa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    /** Runs document ingestion in the background (used by @Async). */
    @Bean(name = "ingestExecutor")
    public TaskExecutor ingestExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(3); // index up to 3 files at the same time; the rest wait in the queue
        ex.setMaxPoolSize(3);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("ingest-");
        ex.initialize();
        return ex;
    }

    /** Runs streaming chat answers so the servlet thread is released. */
    @Bean(name = "chatExecutor")
    public TaskExecutor chatExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("chat-");
        ex.initialize();
        return ex;
    }
}
