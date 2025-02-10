package com.tang.demo_db.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tang.demo_db.dto.RecommendationResponseDTO;
import com.tang.demo_db.dto.RestaurantDTO;
import com.tang.demo_db.dto.ReviewDTO;
import com.tang.demo_db.entity.Restaurant;
import com.tang.demo_db.entity.Review;
import com.tang.demo_db.repository.RestaurantRepository;
import com.tang.demo_db.repository.ReviewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final ReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public RestaurantService(RestaurantRepository restaurantRepository, ReviewRepository reviewRepository) {
        this.restaurantRepository = restaurantRepository;
        this.reviewRepository = reviewRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public ResponseEntity<?> processApiResponse(String apiResponse) {
        try {
            RecommendationResponseDTO response = objectMapper.readValue(apiResponse, RecommendationResponseDTO.class);

            if (!response.isSuccess() || response.getRestaurants() == null || response.getRestaurants().isEmpty()) {
                return ResponseEntity.ok(response);
            }

            List<Restaurant> savedRestaurants = response.getRestaurants().stream()
                    .map(this::saveOrUpdateRestaurant)
                    .collect(Collectors.toList());

            log.info("Processed {} restaurants", savedRestaurants.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing restaurant data", e);
            return ResponseEntity.badRequest()
                    .body("Error processing restaurant data: " + e.getMessage());
        }
    }

    @Transactional
    public ResponseEntity<?> processApiResponseAndGetTop(String apiResponse, int limit) {
        try {
            RecommendationResponseDTO response = objectMapper.readValue(apiResponse, RecommendationResponseDTO.class);

            if (!response.isSuccess() || response.getRestaurants() == null || response.getRestaurants().isEmpty()) {
                return ResponseEntity.ok(response);
            }

            List<Restaurant> savedRestaurants = response.getRestaurants().stream()
                    .map(this::saveOrUpdateRestaurant)
                    .collect(Collectors.toList());

            log.info("Processed {} restaurants", savedRestaurants.size());

            RecommendationResponseDTO limitedResponse = new RecommendationResponseDTO();
            limitedResponse.setSuccess(response.isSuccess());
            limitedResponse.setMessage(response.getMessage());

            List<RestaurantDTO> topRestaurants = response.getRestaurants().stream()
                    .sorted((r1, r2) -> Double.compare(
                            r2.getAdjustedScore() != null ? r2.getAdjustedScore() : 0,
                            r1.getAdjustedScore() != null ? r1.getAdjustedScore() : 0))
                    .limit(limit)
                    .collect(Collectors.toList());

            limitedResponse.setRestaurants(topRestaurants);

            return ResponseEntity.ok(limitedResponse);

        } catch (Exception e) {
            log.error("Error processing restaurant data", e);
            return ResponseEntity.badRequest()
                    .body("Error processing restaurant data: " + e.getMessage());
        }
    }

    @Transactional
    protected Restaurant saveOrUpdateRestaurant(RestaurantDTO dto) {
        Optional<Restaurant> existingRestaurantOpt = restaurantRepository.findByPlaceId(dto.getPlaceId());
        Restaurant restaurant;

        if (existingRestaurantOpt.isPresent()) {
            restaurant = existingRestaurantOpt.get();
            updateReviews(restaurant, dto.getReviews());
        } else {
            restaurant = new Restaurant();
            createInitialReviews(restaurant, dto.getReviews());
        }

        // Update basic fields
        restaurant.setName(dto.getName());
        restaurant.setAddress(dto.getAddress());
        restaurant.setRating(dto.getRating());
        restaurant.setUserRatingsTotal(dto.getUserRatingsTotal());
        restaurant.setPlaceId(dto.getPlaceId());
        restaurant.setPriceLevel(dto.getPriceLevel());
        restaurant.setPhone(dto.getPhone());
        restaurant.setWebsite(dto.getWebsite());

        if (dto.getLocation() != null) {
            restaurant.setLatitude(dto.getLocation().getLat());
            restaurant.setLongitude(dto.getLocation().getLng());
        }

        if (dto.getTypes() != null) {
            restaurant.setTypes(String.join(",", dto.getTypes()));
        }

        if (dto.getPhotos() != null) {
            try {
                restaurant.setPhotos(objectMapper.writeValueAsString(dto.getPhotos()));
            } catch (Exception e) {
                log.error("Error converting photos to JSON", e);
            }
        }

        restaurant.setPredictedScore(dto.getPredictedScore());
        restaurant.setAdjustedScore(dto.getAdjustedScore());

        if (dto.getOpeningHours() != null) {
            try {
                restaurant.setOpeningHours(objectMapper.writeValueAsString(dto.getOpeningHours()));
            } catch (Exception e) {
                log.error("Error converting opening hours to JSON", e);
            }
        }

        return restaurantRepository.save(restaurant);
    }

    private void updateReviews(Restaurant restaurant, List<ReviewDTO> newReviewDTOs) {
        if (newReviewDTOs == null || newReviewDTOs.isEmpty()) {
            return;
        }

        // Create a map of existing reviews for easy lookup
        var existingReviewMap = restaurant.getReviews().stream()
                .collect(Collectors.toMap(
                        this::getReviewKey,
                        review -> review
                ));

        // Create a list to hold reviews to be added
        List<Review> reviewsToAdd = new ArrayList<>();

        // Process each new review DTO
        for (ReviewDTO reviewDTO : newReviewDTOs) {
            String reviewKey = getReviewKey(reviewDTO);
            Review existingReview = existingReviewMap.get(reviewKey);

            if (existingReview != null) {
                // Update existing review
                updateReviewFromDTO(existingReview, reviewDTO);
            } else {
                // Create new review
                Review newReview = createReviewFromDTO(reviewDTO, restaurant);
                reviewsToAdd.add(newReview);
            }
        }

        // Save new reviews
        if (!reviewsToAdd.isEmpty()) {
            List<Review> savedNewReviews = reviewRepository.saveAll(reviewsToAdd);
            restaurant.getReviews().addAll(savedNewReviews);
        }
    }

    private void createInitialReviews(Restaurant restaurant, List<ReviewDTO> reviewDTOs) {
        if (reviewDTOs == null || reviewDTOs.isEmpty()) {
            return;
        }

        List<Review> initialReviews = reviewDTOs.stream()
                .map(dto -> createReviewFromDTO(dto, restaurant))
                .collect(Collectors.toList());

        restaurant.setReviews(initialReviews);
    }

    private String getReviewKey(Review review) {
        return review.getAuthorName() + "_" + review.getTime();
    }

    private String getReviewKey(ReviewDTO reviewDTO) {
        return reviewDTO.getAuthorName() + "_" + reviewDTO.getTime();
    }

    private void updateReviewFromDTO(Review review, ReviewDTO dto) {
        review.setRating(dto.getRating());
        review.setText(dto.getText());
        review.setRelativeTimeDescription(dto.getRelativeTimeDescription());
    }

    private Review createReviewFromDTO(ReviewDTO dto, Restaurant restaurant) {
        Review review = new Review();
        review.setRestaurant(restaurant);
        review.setAuthorName(dto.getAuthorName());
        review.setRating(dto.getRating());
        review.setText(dto.getText());
        review.setTime(dto.getTime());
        review.setRelativeTimeDescription(dto.getRelativeTimeDescription());
        return review;
    }
}