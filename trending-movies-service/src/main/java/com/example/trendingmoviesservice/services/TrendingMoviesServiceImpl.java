package com.example.trendingmoviesservice.services;

import com.example.trendingmoviesservice.grpc.Movie;
import com.example.trendingmoviesservice.grpc.TrendingMoviesRequest;
import com.example.trendingmoviesservice.grpc.TrendingMoviesResponse;
import com.example.trendingmoviesservice.grpc.TrendingMoviesServiceGrpc;
import com.example.trendingmoviesservice.repositories.MovieRatingProjection;
import com.example.trendingmoviesservice.repositories.TrendingRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
public class TrendingMoviesServiceImpl
        extends TrendingMoviesServiceGrpc.TrendingMoviesServiceImplBase {

    private final TrendingRepository trendingRepository;
    private final RestTemplate restTemplate;

    public TrendingMoviesServiceImpl(TrendingRepository trendingRepository,
                                     RestTemplate restTemplate) {
        this.trendingRepository = trendingRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    public void getTrendingMovies(TrendingMoviesRequest request,
                                  StreamObserver<TrendingMoviesResponse> responseObserver) {

        int limit = request.getLimit();
        if (limit <= 0) limit = 10;

        List<MovieRatingProjection> moviesRating =
                trendingRepository.findTopMoviesByAverageRating(limit);

        List<Movie> topMovies = moviesRating.stream()
                .map(row -> {

                    int movieId = row.getMovieId();
                    double avgRating = row.getAvgRating();

                    com.example.trendingmoviesservice.models.Movie movieDto =
                            restTemplate.getForObject(
                                    "http://movie-info-service/movies/" + movieId,
                                    com.example.trendingmoviesservice.models.Movie.class
                            );

                    String name = (movieDto != null) ? movieDto.getName() : "Unknown";

                    return Movie.newBuilder()
                            .setId(movieId)
                            .setName(name)
                            .setRating(avgRating)
                            .build();
                })
                .collect(Collectors.toList());

        TrendingMoviesResponse response = TrendingMoviesResponse.newBuilder()
                .addAllMovies(topMovies)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}