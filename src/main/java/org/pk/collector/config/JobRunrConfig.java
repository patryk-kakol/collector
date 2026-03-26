package org.pk.collector.config;

import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.utils.mapper.JsonMapper;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobRunrConfig {

    @Bean
    public JsonMapper jsonMapper() {
        return new JacksonJsonMapper();
    }
}