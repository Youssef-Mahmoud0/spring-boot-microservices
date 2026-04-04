package com.example.ratingsservice.services;

import com.example.ratingsservice.models.RatingEntity;
import com.example.ratingsservice.repositories.RatingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RatingService {

    @Autowired
    private RatingRepository ratingRepository;

    public List<RatingEntity> getRatingsByUserId(String userId) {
        return ratingRepository.findByUserId(userId);
    }

}

