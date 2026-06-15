package com.sprintplanner.infrastructure.external;

import com.sprintplanner.infrastructure.config.TrelloConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class TrelloApiClient {

    private final TrelloConfig config;
    private final RestTemplate restTemplate;

    public TrelloApiClient(TrelloConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    public com.sprintplanner.infrastructure.config.TrelloConfig getConfig() {
        return config;
    }

    /**
     * Resolves the token to use for Trello API calls.
     * Prefers the user token if provided, falls back to server-side config token.
     */
    public String resolveToken(String userToken) {
        if (userToken != null && !userToken.isBlank()) {
            return userToken;
        }
        if (config.getToken() != null && !config.getToken().isBlank()) {
            return config.getToken();
        }
        throw new IllegalStateException("No Trello token available. Connect your Trello account first.");
    }

    private void validateApiKey() {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "Trello API key not configured. Set TRELLO_API_KEY environment variable.");
        }
    }

    private String auth(String token) {
        return "key=" + config.getApiKey() + "&token=" + token;
    }

    public List<TrelloList> getBoardLists(String boardId, String token) {
        validateApiKey();
        String resolvedToken = resolveToken(token);
        String url = config.getBaseUrl() + "/boards/" + boardId + "/lists?" + auth(resolvedToken);
        try {
            TrelloList[] lists = restTemplate.getForObject(url, TrelloList[].class);
            return lists == null ? List.of() : List.of(lists);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get lists for board " + boardId + ": " + e.getMessage());
        }
    }

    public String findOrCreateList(String boardId, String listName, String token) {
        validateApiKey();
        List<TrelloList> existing = getBoardLists(boardId, token);
        return existing.stream()
                .filter(l -> l.name().equalsIgnoreCase(listName))
                .map(TrelloList::id)
                .findFirst()
                .orElseGet(() -> createList(boardId, listName, token));
    }

    private String createList(String boardId, String listName, String token) {
        String resolvedToken = resolveToken(token);
        String url = config.getBaseUrl() + "/boards/" + boardId + "/lists?"
                + auth(resolvedToken) + "&name=" + encode(listName) + "&idBoard=" + boardId;
        try {
            TrelloList created = restTemplate.postForObject(url, null, TrelloList.class);
            if (created == null || created.id() == null) {
                throw new RuntimeException("Trello returned empty response for list creation");
            }
            return created.id();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create list '" + listName + "': " + e.getMessage());
        }
    }

    public String createCard(String listId, String name, String description, String trelloMemberId, String token) {
        validateApiKey();
        String resolvedToken = resolveToken(token);
        String url = config.getBaseUrl() + "/cards?key=" + config.getApiKey() + "&token=" + resolvedToken;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name != null ? name : "");
        body.put("desc", description != null ? description : "");
        body.put("idList", listId);
        if (trelloMemberId != null && !trelloMemberId.isBlank()) {
            body.put("idMembers", List.of(trelloMemberId));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null || responseBody.get("id") == null) {
                throw new RuntimeException("Trello returned empty response for card creation");
            }
            return (String) responseBody.get("id");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create card '" + name + "': " + e.getMessage());
        }
    }

    public List<TrelloMemberDto> getBoardMembers(String boardId, String token) {
        validateApiKey();
        String resolvedToken = resolveToken(token);
        String url = config.getBaseUrl() + "/boards/" + boardId + "/members?"
                + auth(resolvedToken) + "&fields=id,fullName,username";
        try {
            TrelloMemberDto[] members = restTemplate.getForObject(url, TrelloMemberDto[].class);
            return members == null ? List.of() : List.of(members);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get members for board " + boardId + ": " + e.getMessage());
        }
    }

    public List<TrelloList> getUserBoards(String token) {
        validateApiKey();
        String resolvedToken = resolveToken(token);
        String url = config.getBaseUrl() + "/members/me/boards?" + auth(resolvedToken) + "&fields=name,id,url";
        try {
            TrelloList[] boards = restTemplate.getForObject(url, TrelloList[].class);
            return boards == null ? List.of() : List.of(boards);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user boards: " + e.getMessage());
        }
    }

    private String encode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
