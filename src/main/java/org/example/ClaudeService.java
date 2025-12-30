package org.example;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClaudeService {
  private static final Logger logger = LogManager.getLogger(ClaudeService.class);
  private final AnthropicClient anthropicClient;
  private static final String DEFAULT_MODEL = "claude-haiku-4-5-20251001";

  public ClaudeService() {
    String apiKey = System.getProperty("CLAUDE_API_KEY");
    if (apiKey == null || apiKey.isEmpty()) {
      throw new IllegalStateException("CLAUDE_API_KEY environment variable is not set. Please set it in .env file.");
    }
    this.anthropicClient =
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .build();
    logger.info("ClaudeService initialized successfully");
  }

  public void getResponse(List<TelegramBot.ChatMessage> history, Consumer<String> messageSender) {
    try {
      logger.debug("Sending {} messages to Claude", history.size());

      // Convert chat history to MessageParam list
      List<MessageParam> messageParams = new ArrayList<>();
      messageParams.add(
          MessageParam.builder()
              .role(MessageParam.Role.USER)
              .content(
                  "Your responses are going to be shown in a Telegram chat. Don't use Markdownor"
                      + " any other formatting that won't be recognised by the messenger, make the"
                      + " text presentable taking into account the messenger limitations. ")
              .build());
      for (TelegramBot.ChatMessage chatMsg : history) {
        MessageParam.Role role =
            chatMsg.isUser() ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT;

        messageParams.add(MessageParam.builder().role(role).content(chatMsg.text()).build());
      }

      MessageCreateParams params =
          MessageCreateParams.builder()
              .model(DEFAULT_MODEL)
              .maxTokens(25000)
              .messages(messageParams)
              .addTool(WebSearchTool20250305.builder().build())
              .build();

      var responseBuilder = new StringBuilder();
      try (var streamResponse = anthropicClient.messages().createStreaming(params)) {
        streamResponse.stream()
            .forEach(
                chunk -> {
                  logger.info("content: {}", chunk);
                  if (chunk.isContentBlockDelta() && chunk.asContentBlockDelta().delta().isText()) {
                    String s = chunk.asContentBlockDelta().delta().asText().text();
                    var s_split = s.split("\n");
                    for (int i = 0; i < s_split.length; i++) {
                      responseBuilder.append(s_split[i]);
                      if (i + 1 < s_split.length) {
                        responseBuilder.append("\n");
                      }
                      if ((s_split[i].endsWith(".") && responseBuilder.length() > 4000)
                          || (i + 1 < s_split.length && responseBuilder.length() > 600)) {
                        messageSender.accept(responseBuilder.toString());
                        responseBuilder.setLength(0);
                      }
                    }
                  }
                  if (chunk.isContentBlockStop() && responseBuilder.length() > 0) {
                    responseBuilder.append("\n");
                  }
                });
        logger.debug("consumed streaming response");
      }
      if (!responseBuilder.isEmpty()) {
        messageSender.accept(responseBuilder.toString());
      }

    } catch (Exception e) {
      logger.error("Error communicating with Claude API", e);
      messageSender.accept(
          "Sorry, I encountered an error while processing your message. Please try again later.");
    }
  }

  private String extractTextContent(Message message) {
    if (!message.isValid() || message.content().isEmpty()) {
      return "I received your message but couldn't generate a response.";
    }

    // Extract text from content blocks
    StringBuilder response = new StringBuilder();
    for (var contentBlock : message.content()) {
      if (contentBlock.isText()) {
        response.append(contentBlock.asText().text());
        response.append('\n');
      }
    }

    return response.isEmpty()
        ? "I received your message but couldn't generate a response."
        : response.toString();
  }
}
