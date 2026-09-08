package bolsilloApp.api.chat;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.chat.dto.ChatHistoryResponse;
import bolsilloApp.api.chat.dto.ChatRequest;
import bolsilloApp.api.chat.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(currentUser.getId(), request));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatHistoryResponse>> getHistory(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) LocalDateTime before) {
        return ResponseEntity.ok(chatService.getHistory(currentUser.getId(), limit, before));
    }
}
