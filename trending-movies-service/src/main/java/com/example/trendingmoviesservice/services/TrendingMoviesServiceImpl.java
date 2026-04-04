package com.example.trendingmoviesservice.services;

import com.example.trendingmoviesservice.grpc.Movie;
import com.example.trendingmoviesservice.grpc.TrendingMoviesRequest;
import com.example.trendingmoviesservice.grpc.TrendingMoviesResponse;
import com.example.trendingmoviesservice.grpc.TrendingMoviesServiceGrpc;
import com.example.trendingmoviesservice.models.Rating;
import com.example.trendingmoviesservice.repositories.TrendingRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@GrpcService
public class TrendingMoviesServiceImpl
        extends TrendingMoviesServiceGrpc.TrendingMoviesServiceImplBase {

    private final TrendingRepository trendingRepository;
    private final RestTemplate restTemplate;


    public TrendingMoviesServiceImpl(TrendingRepository trendingRepository,
                                     RestTemplate restTemplate
                                    ) {
        this.trendingRepository = trendingRepository;
        this.restTemplate = restTemplate;
    }


    @Override
    public void getTrendingMovies(TrendingMoviesRequest request,
                                  StreamObserver<TrendingMoviesResponse> responseObserver) {

        System.out.println("Received request for trending movies with limit: " + request.getLimit());

        int limit = request.getLimit();
        if (limit <= 0) limit = 10;


        // Query the database to get the top movies by average rating.
        List<Objects[]> moviesRating = trendingRepository.findTopMoviesByAverageRating(limit);

        System.out.println("Fetched " + moviesRating.size() + " movies from the database for trending list.");

        // --- WHERE THIS DATA COMES FROM ---
        // In the full solution, you inject your RatingsRepository here and run:
        // SELECT movie_id, AVG(rating) FROM ratings GROUP BY movie_id ORDER BY AVG(rating) DESC LIMIT ?
        //
        // For now we use hardcoded data that simulates query results:
//        List<Movie> allMovies = new ArrayList<>();
//        allMovies.add(buildMovie(1, "The Shawshank Redemption", 9.3));
//        allMovies.add(buildMovie(2, "The Godfather", 9.2));
//        allMovies.add(buildMovie(3, "The Dark Knight", 9.0));
//        allMovies.add(buildMovie(4, "Pulp Fiction", 8.9));
//        allMovies.add(buildMovie(5, "Schindler's List", 8.9));
//        allMovies.add(buildMovie(6, "The Lord of the Rings", 8.8));
//        allMovies.add(buildMovie(7, "Fight Club", 8.8));
//        allMovies.add(buildMovie(8, "Forrest Gump", 8.8));
//        allMovies.add(buildMovie(9, "Inception", 8.7));
//        allMovies.add(buildMovie(10, "Goodfellas", 8.7));
//        allMovies.add(buildMovie(11, "The Matrix", 8.6));
//        allMovies.add(buildMovie(12, "Interstellar", 8.6));
//
//        allMovies.sort(Comparator.comparingDouble(Movie::getRating).reversed());
//        List<Movie> topMovies = allMovies.subList(0, Math.min(limit, allMovies.size()));

        List<Movie> topMovies = moviesRating.stream()
                .map(row -> {
                    int movieId = Integer.parseInt(String.valueOf(row[0]));
                    double avgRating = Double.parseDouble(String.valueOf(row[1]));

                    System.out.println("Processing movieId: " + movieId + " with avgRating: " + avgRating);

                    // Call movie-info-service the same way the catalog service does
                    // @LoadBalanced RestTemplate resolves "movie-info-service" via Eureka
                    Movie movie = restTemplate.getForObject(
                            "http://movie-info-service/movies/" + movieId,
                            Movie.class
                    );

                    return Movie.newBuilder()
                            .setId(movieId)
                            .setName(movie.getName())
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



    private Movie buildMovie(int id, String title, double rating) {
        return Movie.newBuilder()
                .setId(id)
                .setName(title)
                .setRating(rating)
                .build();
    }
}