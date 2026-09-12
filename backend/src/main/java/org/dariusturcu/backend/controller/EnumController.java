package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dariusturcu.backend.model.song.Country;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enums")
@Tag(name = "Enums", description = "Returns available enum values")
public class EnumController {
    @Operation(summary = "Get all available countries")
    @GetMapping("/countries")
    public ResponseEntity<Country[]> getCountries() {
        return ResponseEntity.ok(Country.values());
    }
}
