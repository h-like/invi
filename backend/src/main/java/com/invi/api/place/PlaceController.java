package com.invi.api.place;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Venue search for the editor's map block. Login required (the /api/** default) to protect the API quota. */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping("/search")
    public List<PlaceDto> search(@RequestParam(defaultValue = "") String query) {
        try {
            return placeSearchService.searchVenues(query);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
