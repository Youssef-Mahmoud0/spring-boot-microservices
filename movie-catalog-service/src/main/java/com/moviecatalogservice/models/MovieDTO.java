package com.moviecatalogservice.models;


public class MovieDTO {
    private int movieId;
    private String title;
    private double rating;

    public MovieDTO(int movieId, String title, double rating) {
        this.movieId = movieId;
        this.title = title;
        this.rating = rating;
    }

    public int getMovieId() {
        return movieId;
    }

    public void setMovieId(int movieId) {
        this.movieId = movieId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }
}