package com.example.movieinfoservice.repository;

import com.example.movieinfoservice.models.MovieCache;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieCacheRepository extends MongoRepository<MovieCache, String> {

}