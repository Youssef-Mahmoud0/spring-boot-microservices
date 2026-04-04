package com.example.ratingsservice.services;

import com.example.ratingsservice.models.RatingEntity;
import com.example.ratingsservice.repositories.RatingRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RatingService {

    private final RatingRepository repository;

    public RatingService(RatingRepository repository) {
        this.repository = repository;
    }

    public List<RatingEntity> getRatingsByUserId(String userId) {
        return repository.findByUserId(userId);
    }
}