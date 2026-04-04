package com.example.ratingsservice.repositories;

import com.example.ratingsservice.models.RatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RatingRepository extends JpaRepository<RatingEntity, Integer> {
    List<RatingEntity> findByUserId(String userId);
}