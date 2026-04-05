package com.example.ratingsservice.services;

import com.example.ratingsservice.models.Rating;
import com.example.ratingsservice.models.RatingEntity;
import com.example.ratingsservice.repositories.RatingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RatingService {

    @Autowired
    private RatingRepository ratingRepository;

    public List<Rating> getRatingsByUserId(String userId) {
        return ratingRepository.findByUserId(userId)
                .stream()
                .map(entity -> new Rating(
                        entity.getMovieId(),
                        entity.getRating()
                ))
                .collect(Collectors.toList());
    }
}