package com.example.movieinfoservice;

import com.example.movieinfoservice.repository.MovieCacheRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableEurekaClient
@EnableCircuitBreaker
public class MovieInfoServiceApplication {

    private final int TIMEOUT = 3000;   // 3 seconds

    @Bean
    public RestTemplate getRestTemplate() {
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(TIMEOUT);   // Set the timeout to 3 seconds
        return new RestTemplate();
    }

    public static void main(String[] args) {
        SpringApplication.run(MovieInfoServiceApplication.class, args);
    }
    @Bean
    public CommandLineRunner clearCacheOnStartup(MovieCacheRepository movieCacheRepository) {
        return args -> {
            long count = movieCacheRepository.count();
            movieCacheRepository.deleteAll();
            System.out.println("✅ Cache truncated on startup. Removed " + count + " entries.");
        };
    }

}
