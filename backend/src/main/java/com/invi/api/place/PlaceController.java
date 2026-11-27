package com.invi.api.place;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Place search for the editor's map block: {@code scope=venue} (default) keeps wedding venues only,
 * {@code scope=all} is the fallback for ceremonies held elsewhere. Login required (the /api/** default)
 * to protect the API quota.
 */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping("/search")
    public List<PlaceDto> search(
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "venue") String scope) {
        try {
            return switch (scope) {
                case "venue" -> placeSearchService.searchVenues(query);
                case "all" -> placeSearchService.searchAll(query);
                default -> throw new IllegalArgumentException("알 수 없는 검색 범위입니다.");
            };
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
