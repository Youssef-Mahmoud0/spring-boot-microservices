package com.moviecatalogservice.services;

import com.example.trendingmoviesservice.grpc.TrendingMoviesRequest;
import com.example.trendingmoviesservice.grpc.TrendingMoviesResponse;
import com.example.trendingmoviesservice.grpc.TrendingMoviesServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class TrendingMoviesService {

    // "trending-movies-service" must exactly match the key in application.properties:
    // grpc.client.trending-movies-service.address=...
    @GrpcClient("trending-movies-service")
    private TrendingMoviesServiceGrpc.TrendingMoviesServiceBlockingStub stub;

    public TrendingMoviesResponse getTopTrendingMovies(int limit) {
        TrendingMoviesRequest request = TrendingMoviesRequest.newBuilder()
                .setLimit(limit)
                .build();

        // let's call the gRPC service and return the response
        // let the controller handle the conversion back to Movie objects
        return stub.getTrendingMovies(request);
    }
}