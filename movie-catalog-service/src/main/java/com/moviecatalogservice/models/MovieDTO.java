package com.moviecatalogservice.models;


public class MovieDTO {
    private int movieId;
    private String name;
    private double rating;

    public MovieDTO(int movieId, String title, double rating) {
        this.movieId = movieId;
        this.name = title;
        this.rating = rating;
    }

    public int getMovieId() {
        return movieId;
    }

    public void setMovieId(int movieId) {
        this.movieId = movieId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }
}