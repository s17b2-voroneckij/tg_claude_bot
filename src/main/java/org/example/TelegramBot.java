package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class TelegramBot implements LongPollingSingleThreadUpdateConsumer {
  private static final Logger logger = LogManager.getLogger(TelegramBot.class);

  private final String botToken;
  private final TelegramClient telegramClient;
  private final ClaudeService claudeService;

  // Store chat history per chatId. Each list contains messages since the last /new command
  private final Map<Long, List<ChatMessage>> chatHistory = new ConcurrentHashMap<>();

  public TelegramBot() {
    this.botToken = System.getProperty("TELEGRAM_BOT_TOKEN");
    if (botToken == null || botToken.isEmpty()) {
      throw new IllegalStateException("TELEGRAM_BOT_TOKEN environment variable is not set. Please set it in .env file.");
    }
    this.telegramClient = new OkHttpTelegramClient(botToken);
    this.claudeService = new ClaudeService();
  }

  public String getBotToken() {
    return botToken;
  }

  @Override
  public void consume(Update update) {
    // Check if the update has a message and the message has text
    logger.info("received update: {}", update);
    if (update.hasMessage() && update.getMessage().hasText()) {
      var message = update.getMessage();
      if (message.getFrom().getUserName().equals("Voron179")
          || message.getFrom().getUserName().equals("ilya_pauzner")) {
        String messageText = message.getText();
        long chatId = message.getChatId();
        String userName = message.getFrom().getFirstName();

        logger.debug("Received message from {} (chatId: {}): {}", userName, chatId, messageText);

        // Handle commands
        if (messageText.startsWith("/")) {
          handleCommand(messageText, chatId, userName);
        } else {
          // Add user message to history
          addToHistory(chatId, messageText, true);
          // Handle regular messages with history
          handleMessage(messageText, chatId, userName);
        }
      }
    }
  }

  private void handleCommand(String command, long chatId, String userName) {
    var message = SendMessage.builder();
    message.chatId(String.valueOf(chatId));

    switch (command) {
      case "/start":
        message.text(
            "Hello, " + userName + "! Welcome to the bot. Use /help to see available commands.");
        break;
      case "/help":
        message.text(
            "Available commands:\n"
                + "/start - Start the bot\n"
                + "/help - Show this help message\n"
                + "/new - Start a new conversation (clear chat history)\n\n"
                + "Just send me a message and I'll forward it to Claude AI for a response!");
        break;
      case "/new":
        // Clear chat history for this user
        chatHistory.remove(chatId);
        message.text("Started a new conversation. Previous chat history has been cleared.");
        logger.debug("Cleared chat history for chatId: {}", chatId);
        break;
      case "/echo":
        message.text("Usage: /echo <your message>");
        break;
      default:
        if (command.startsWith("/echo ")) {
          String echoText = command.substring(6);
          message.text("Echo: " + echoText);
        } else {
          message.text("Unknown command. Use /help to see available commands.");
        }
        break;
    }

    try {
      telegramClient.execute(message.build());
      logger.debug("Sent command response to chatId: {}", chatId);
    } catch (TelegramApiException e) {
      logger.error("Error sending command response to chatId: {}", chatId, e);
    }
  }

  private void handleMessage(String messageText, long chatId, String userName) {
    try {
      // Get chat history for this user (messages since last /new)
      List<ChatMessage> history = chatHistory.getOrDefault(chatId, new ArrayList<>());

      logger.info(
          "Sending message to Claude from user {} (chatId: {}) with {} messages in history",
          userName,
          chatId,
          history.size());

      telegramClient.execute(
          SendChatAction.builder().action("typing").chatId(String.valueOf(chatId)).build());
      // Get Claude's response with full history
      claudeService.getResponse(
          history,
          (response) -> {
            // Add Claude's response to history
            addToHistory(chatId, response, false);

            // Send Claude's response back to the user
            var message = SendMessage.builder();
            message.chatId(String.valueOf(chatId));
            message.text(response);

            try {
              telegramClient.execute(message.build());
              telegramClient.execute(
                  SendChatAction.builder().action("typing").chatId(String.valueOf(chatId)).build());
            } catch (TelegramApiException e) {
              throw new RuntimeException(e);
            }
          });
      logger.debug("Sent Claude response to chatId: {}", chatId);
    } catch (TelegramApiException e) {
      logger.error("Error sending message response to chatId: {}", chatId, e);
      // Try to send an error message
      try {
        SendMessage.SendMessageBuilder<?, ?> errorMessage = SendMessage.builder();
        errorMessage.chatId(String.valueOf(chatId));
        errorMessage.text("Sorry, I encountered an error sending the response. Please try again.");
        telegramClient.execute(errorMessage.build());
      } catch (TelegramApiException ex) {
        logger.error("Failed to send error message", ex);
      }
    }
  }

  private void addToHistory(long chatId, String text, boolean isUser) {
    chatHistory.computeIfAbsent(chatId, k -> new ArrayList<>()).add(new ChatMessage(text, isUser));
    logger.debug("Added message to history for chatId: {} (isUser: {})", chatId, isUser);
  }

  // Simple class to store chat messages
  public record ChatMessage(String text, boolean isUser) {}
}
