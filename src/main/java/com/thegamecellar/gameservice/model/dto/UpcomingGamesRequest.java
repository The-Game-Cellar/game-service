package com.thegamecellar.gameservice.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
public class UpcomingGamesRequest {

    private String platform;

    @Min(0) @Max(3650)
    private Integer windowDays = 90;

    @Min(1) @Max(100)
    private Integer limit = 20;

    @Size(max = 100)
    private Set<Integer> excludeIds;

    // Soft-penalty input for the weighted sampler. Hard cap at 2000 mirrors the frontend
    // localStorage cap; entries beyond that contribute no extra value because the sampler
    // touches them in O(pool) per pick.
    @Size(max = 2000)
    private List<Integer> recentlyShownIds;

    // Presence of page switches the service from sample mode (Dashboard rotation) to
    // deterministic sort-by-release-date pagination (Explore browse view). When null,
    // recentlyShownIds + sampling apply; when set, results are stable across refreshes.
    @Min(0) @Max(500)
    private Integer page;

    @Min(1) @Max(100)
    private Integer pageSize;
}
