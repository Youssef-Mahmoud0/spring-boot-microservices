package com.example.movieinfoservice.resources;

import com.example.movieinfoservice.models.Movie;
import com.example.movieinfoservice.models.MovieCache;
import com.example.movieinfoservice.models.MovieSummary;
import com.example.movieinfoservice.repository.MovieCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.Null;
import java.util.Optional;
import java.util.TimeZone;

@RestController
@RequestMapping("/movies")
public class MovieResource {

    @Value("${api.key}")
    private String apiKey;

    final private RestTemplate restTemplate;

    final private MovieCacheRepository movieCacheRepository;

    public MovieResource(RestTemplate restTemplate, MovieCacheRepository movieCacheRepository) {
        this.restTemplate = restTemplate;
        this.movieCacheRepository = movieCacheRepository;
    }

    @RequestMapping("/{movieId}")
    public ResponseEntity<Movie> getMovieInfo(@PathVariable("movieId") String movieId, TimeZone timeZone) {

        // Check MongoDB for cached request.
        Optional<MovieCache> cached = movieCacheRepository.findById(movieId);
        if (cached.isPresent()) {
            MovieCache cache = cached.get();
            if(cache.getName().equals("NO_MOVIE_FOUND WITH THIS ID")) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(new Movie(cache.getMovieId(), cache.getName(), cache.getDescription()));
        };

        // Cache Miss -> Return from API
        final String url = "https://api.themoviedb.org/3/movie/" + movieId + "?api_key=" + apiKey;
        try {
            MovieSummary movieSummary = restTemplate.getForObject(url, MovieSummary.class);

            Movie movie = new Movie(movieId, movieSummary.getTitle(), movieSummary.getOverview());
            MovieCache movieCache = new MovieCache(movie.getMovieId(), movie.getName(), movie.getDescription());
            movieCacheRepository.save(movieCache);

            return ResponseEntity.status(201).body(movie);
        } catch (HttpClientErrorException.NotFound e) {
            Movie movie = new Movie(movieId, "NO_MOVIE_FOUND WITH THIS ID", "");
            MovieCache movieCache = new MovieCache(movie.getMovieId(), movie.getName(), movie.getDescription());
            movieCacheRepository.save(movieCache);
            return ResponseEntity.notFound().build();
        }
    }

    // no caching test
    @RequestMapping("/no-caching/{movieId}")
    public ResponseEntity<Movie> getMovieInfoNoCaching(@PathVariable("movieId") String movieId, TimeZone timeZone) {
        try {
            final String url = "https://api.themoviedb.org/3/movie/" + movieId + "?api_key=" + apiKey;
            MovieSummary movieSummary = restTemplate.getForObject(url, MovieSummary.class);

            return ResponseEntity.ok(new Movie(movieId, movieSummary.getTitle(), movieSummary.getOverview()));
        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

}
