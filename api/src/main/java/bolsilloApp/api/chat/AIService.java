package bolsilloApp.api.chat;

import bolsilloApp.api.chat.dto.ChatResponse;
import bolsilloApp.api.metrics.dto.MetricsContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AIService {

    public ChatResponse call(String message, ChatIntent intent, MetricsContext context) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
