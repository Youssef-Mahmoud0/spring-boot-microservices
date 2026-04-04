package com.example.ratingsservice.models;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;

@Entity
@Table(name = "ratings")
public class RatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "movie_id")
    private String movieId;

    private int rating;

    // Getters
    public int getRating() { return this.rating; }
    public String getMovieId() { return this.movieId; }
}
