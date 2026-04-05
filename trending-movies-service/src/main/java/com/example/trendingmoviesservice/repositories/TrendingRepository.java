package com.example.trendingmoviesservice.repositories;

import com.example.trendingmoviesservice.models.RatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrendingRepository extends JpaRepository<RatingEntity, Integer> {

    @Query(value = "SELECT movie_id AS movieId, AVG(rating) AS avgRating " +
            "FROM ratings " +
            "GROUP BY movie_id " +
            "ORDER BY avgRating DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<MovieRatingProjection> findTopMoviesByAverageRating(@Param("limit") int limit);
}
