package com.thegamecellar.gameservice.model.dto;

import java.util.List;

// Sorted desc by occurrence count; junk filtered server-side. Reflects live catalog state.
public record PopularTagsResponse(List<String> tags) {}
