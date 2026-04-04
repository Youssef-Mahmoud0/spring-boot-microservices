package com.example.trendingmoviesservice.repositories;

import com.example.trendingmoviesservice.models.Rating;
import com.example.trendingmoviesservice.models.RatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public interface TrendingRepository extends JpaRepository<RatingEntity, Integer> {

    // This query groups all ratings by movie_id, calculates the average rating
    // for each movie, sorts by highest average first, and returns only the top N.
    // The result is a list of Object[] where [0] is movieId and [1] is avgRating.
    @Query(value = "SELECT movie_id, AVG(rating) as avg_rating " +
            "FROM ratings " +
            "GROUP BY movie_id " +
            "ORDER BY avg_rating DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Objects[]> findTopMoviesByAverageRating(@Param("limit") int limit);
}