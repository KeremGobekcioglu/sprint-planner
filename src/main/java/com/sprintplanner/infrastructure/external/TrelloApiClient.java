package com.sprintplanner.infrastructure.external;

import com.sprintplanner.infrastructure.config.TrelloConfig;
import java.util.List;
import java.util.Map;
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

    private void validateCredentials() {
        if (config.getApiKey() == null || config.getApiKey().isBlank() ||
                config.getToken() == null || config.getToken().isBlank()) {
            throw new IllegalStateException(
                    "Trello credentials not configured. Set TRELLO_API_KEY and TRELLO_TOKEN environment variables.");
        }
    }

    private String auth() {
        return "key=" + config.getApiKey() + "&token=" + config.getToken();
    }

    public List<TrelloList> getBoardLists(String boardId) {
        validateCredentials();
        String url = config.getBaseUrl() + "/boards/" + boardId + "/lists?" + auth();
        try {
            TrelloList[] lists = restTemplate.getForObject(url, TrelloList[].class);
            return lists == null ? List.of() : List.of(lists);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get lists for board " + boardId + ": " + e.getMessage());
        }
    }

    public String findOrCreateList(String boardId, String listName) {
        validateCredentials();
        List<TrelloList> existing = getBoardLists(boardId);
        return existing.stream()
                .filter(l -> l.name().equalsIgnoreCase(listName))
                .map(TrelloList::id)
                .findFirst()
                .orElseGet(() -> createList(boardId, listName));
    }

    private String createList(String boardId, String listName) {
        String url = config.getBaseUrl() + "/boards/" + boardId + "/lists?"
                + auth() + "&name=" + encode(listName) + "&idBoard=" + boardId;
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

    @SuppressWarnings("rawtypes")
    public String createCard(String listId, String name, String description, String trelloMemberId) {
        validateCredentials();
        String url = config.getBaseUrl() + "/cards?"
                + auth() + "&idList=" + listId
                + "&name=" + encode(name)
                + "&desc=" + encode(description);
        if (trelloMemberId != null && !trelloMemberId.isBlank()) {
            url += "&idMembers=" + encode(trelloMemberId);
        }
        try {
            Map response = restTemplate.postForObject(url, null, Map.class);
            if (response == null || response.get("id") == null) {
                throw new RuntimeException("Trello returned empty response for card creation");
            }
            return (String) response.get("id");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create card '" + name + "': " + e.getMessage());
        }
    }

    public List<TrelloMemberDto> getBoardMembers(String boardId) {
        validateCredentials();
        String url = config.getBaseUrl() + "/boards/" + boardId + "/members?"
                + auth() + "&fields=id,fullName,username";
        try {
            TrelloMemberDto[] members = restTemplate.getForObject(url, TrelloMemberDto[].class);
            return members == null ? List.of() : List.of(members);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get members for board " + boardId + ": " + e.getMessage());
        }
    }

    public List<TrelloList> getUserBoards() {
        validateCredentials();
        String url = config.getBaseUrl() + "/members/me/boards?" + auth() + "&fields=name,id,url";
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
