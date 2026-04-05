package com.example.trendingmoviesservice.repositories;

public interface MovieRatingProjection {
    Integer getMovieId();
    Double getAvgRating();
}