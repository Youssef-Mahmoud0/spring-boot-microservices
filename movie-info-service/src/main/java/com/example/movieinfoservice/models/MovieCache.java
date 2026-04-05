package com.example.movieinfoservice.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "movie_cache")
public class MovieCache {

    @Id
    private String movieId;
    private String name;
    private String description;
    private LocalDateTime cachedAt;

    public MovieCache() {}

    public MovieCache(String movieId, String name, String description) {
        this.movieId = movieId;
        this.name = name;
        this.description = description;
        this.cachedAt = LocalDateTime.now();
    }

    public String getMovieId() { return movieId; }
    public void setMovieId(String movieId) { this.movieId = movieId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCachedAt() { return cachedAt; }
    public void setCachedAt(LocalDateTime cachedAt) { this.cachedAt = cachedAt; }
}