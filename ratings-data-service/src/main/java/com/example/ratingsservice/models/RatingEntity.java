package com.example.ratingsservice.models;
import javax.persistence.*;
import lombok.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String userId;
    private String movieId;
    private int rating;

}