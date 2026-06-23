package bolsilloApp.api.chat;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.chat.dto.ChatRequest;
import bolsilloApp.api.chat.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
}
