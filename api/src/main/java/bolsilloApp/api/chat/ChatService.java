package bolsilloApp.api.chat;

import bolsilloApp.api.chat.dto.ChatRequest;
import bolsilloApp.api.chat.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final AIService aiService;
    private final ContextBuilder contextBuilder;

    public ChatResponse chat(UUID userId, ChatRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
