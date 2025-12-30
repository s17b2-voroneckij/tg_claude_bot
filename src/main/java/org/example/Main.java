package org.example;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

public class Main {
  private static final Logger logger = LogManager.getLogger(Main.class);

  public static void main(String[] args) {
    try {
      // Load environment variables from .env file
      Dotenv dotenv = Dotenv.configure().load();
      dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
      
      logger.info("Starting Telegram bot...");
      TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
      TelegramBot bot = new TelegramBot();
      botsApplication.registerBot(bot.getBotToken(), bot);
      logger.info("Telegram bot started successfully!");
    } catch (Exception e) {
      logger.error("Failed to start Telegram bot", e);
    }
  }
}
