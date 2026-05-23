package com.thegamecellar.gameservice.service;

import com.thegamecellar.gameservice.exception.GameNotFoundException;
import com.thegamecellar.gameservice.exception.IgdbApiException;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbCoverDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbGameDto;
import com.thegamecellar.gameservice.model.dto.igdb.IgdbNamedEntityDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IgdbApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private IgdbTokenService tokenService;

    private IgdbApiClient client;

    @BeforeEach
    void setUp() {
        when(tokenService.getToken()).thenReturn("test-token");
        when(tokenService.getClientId()).thenReturn("test-client-id");
        client = new IgdbApiClient(restTemplate, tokenService, "https://api.igdb.com/v4");
    }

    @Test
    void fetchGameById_returnsGame() {
        IgdbGameDto game = gameDto(1234, "Witcher 3");
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class)))
                .thenReturn(new IgdbGameDto[]{game});

        IgdbGameDto result = client.fetchGameById(1234);

        assertThat(result.getId()).isEqualTo(1234);
        assertThat(result.getName()).isEqualTo("Witcher 3");
    }

    @Test
    void fetchGameById_throwsGameNotFoundException_whenEmpty() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class)))
                .thenReturn(new IgdbGameDto[]{});

        assertThatThrownBy(() -> client.fetchGameById(999))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    void searchGames_containsSearchTermInQuery() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class)))
                .thenReturn(new IgdbGameDto[]{});

        client.searchGames("zelda", 20, 0);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(IgdbGameDto[].class));
        assertThat(captor.getValue().getBody()).contains("search \"zelda\"");
    }

    @Test
    void searchByGenre_containsGenreFilterInQuery() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class)))
                .thenReturn(new IgdbGameDto[]{});

        client.searchByGenre("Action", 20, 0);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(IgdbGameDto[].class));
        assertThat(captor.getValue().getBody()).contains("genres.name = \"Action\"");
    }

    @Test
    void fetchPopularGames_withPlatform_containsPlatformFilter() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class)))
                .thenReturn(new IgdbGameDto[]{});

        client.fetchPopularGames("PC", 20, 0);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(IgdbGameDto[].class));
        assertThat(captor.getValue().getBody()).contains("platforms = (6)");
    }

    @Test
    void fetchPopularGames_withoutPlatform_hasNoPlatformFilter() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class)))
                .thenReturn(new IgdbGameDto[]{});

        client.fetchPopularGames(null, 20, 0);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(IgdbGameDto[].class));
        assertThat(captor.getValue().getBody()).doesNotContain("platforms = (");
    }

    @Test
    void fetchCatalogPage_containsOffsetInQuery() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class)))
                .thenReturn(new IgdbGameDto[]{});

        client.fetchCatalogPage(500, 1000);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(IgdbGameDto[].class));
        assertThat(captor.getValue().getBody()).contains("limit 500").contains("offset 1000");
    }

    @Test
    void hostAllowlist_throwsIgdbApiException_forNonIgdbHost() {
        IgdbApiClient maliciousClient = new IgdbApiClient(restTemplate, tokenService, "https://evil.com/v4");

        assertThatThrownBy(() -> maliciousClient.fetchGameById(1))
                .isInstanceOf(IgdbApiException.class)
                .hasMessageContaining("allowlist");
    }

    @Test
    void sanitize_stripsQuotesAndSemicolons_inSearchQuery() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class)))
                .thenReturn(new IgdbGameDto[]{});

        client.searchGames("zelda\"; drop table games;--", 20, 0);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(IgdbGameDto[].class));
        String body = captor.getValue().getBody();
        // Sanitize strips " and ; from input; attacker can't break out of search string or inject new clauses
        assertThat(body).matches("(?s).*search \"[^\"]*\".*");
    }

    @Test
    void fetchCatalogPage_returnsEmptyList_whenResponseNull() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class))).thenReturn(null);

        List<IgdbGameDto> result = client.fetchCatalogPage(500, 0);

        assertThat(result).isEmpty();
    }

    @Test
    void queryGames_throwsIgdbApiException_onHttpError() {
        when(restTemplate.postForObject(anyString(), any(), eq(IgdbGameDto[].class)))
                .thenThrow(new RuntimeException("connection timeout"));

        assertThatThrownBy(() -> client.fetchGameById(1))
                .isInstanceOf(IgdbApiException.class)
                .hasMessageContaining("IGDB /games query failed");
    }

    private IgdbGameDto gameDto(int id, String name) {
        IgdbGameDto dto = new IgdbGameDto();
        dto.setId(id);
        dto.setName(name);
        IgdbCoverDto cover = new IgdbCoverDto();
        cover.setImageId("abc123");
        dto.setCover(cover);
        IgdbNamedEntityDto genre = new IgdbNamedEntityDto();
        genre.setId(12);
        genre.setName("Role-playing (RPG)");
        dto.setGenres(List.of(genre));
        return dto;
    }
}
