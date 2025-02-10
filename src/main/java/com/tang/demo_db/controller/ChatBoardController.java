package com.tang.demo_db.controller;

import com.tang.demo_db.dto.RecommendationResponseDTO;
import com.tang.demo_db.service.ExternalApiService;
import com.tang.demo_db.service.RestaurantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chatboard")
public class ChatBoardController {

    private final ExternalApiService externalApiService;
    private final RestaurantService restaurantService;

    @Autowired
    public ChatBoardController(ExternalApiService externalApiService,
                               RestaurantService restaurantService) {
        this.externalApiService = externalApiService;
        this.restaurantService = restaurantService;
    }

    @PostMapping("/index")
    public ResponseEntity<?> processQuery(@RequestBody String userQuery) {
        try {
            // Call external API
            ResponseEntity<String> apiResponse = externalApiService.getRecommendations(userQuery);

            // Process response and store restaurants
            return restaurantService.processApiResponse(apiResponse.getBody());

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Error processing request: " + e.getMessage());
        }
    }

    @PostMapping("/top-five")  // Changed from /top5 to /top-five for clarity
    public ResponseEntity<?> processQueryTopFive(@RequestBody String userQuery) {
        try {
            ResponseEntity<String> apiResponse = externalApiService.getRecommendations(userQuery);
            return restaurantService.processApiResponseAndGetTop(apiResponse.getBody(), 5);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Error processing request: " + e.getMessage());
        }
    }
}