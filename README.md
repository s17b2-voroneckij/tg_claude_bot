# Telegram Bot Template

A simple Java Telegram bot template using the Telegram Bot API library.

## Setup

1. **Get your bot token:**
   - Open Telegram and search for [@BotFather](https://t.me/BotFather)
   - Send `/newbot` command and follow the instructions
   - Copy the bot token you receive

2. **Configure the bot:**
   - Open `src/main/java/org/example/TelegramBot.java`
   - Replace `YOUR_BOT_TOKEN_HERE` with your actual bot token
   - Replace `YOUR_BOT_USERNAME_HERE` with your bot username (without @)

3. **Build and run:**
   ```bash
   mvn clean compile
   mvn exec:java -Dexec.mainClass="org.example.Main"
   ```

## Features

- Basic command handling (`/start`, `/help`, `/echo`)
- Message handling for regular text messages
- Easy to extend with additional commands and features

## Adding New Commands

To add a new command, modify the `handleCommand` method in `TelegramBot.java`:

```java
case "/yourcommand":
    message.setText("Your response here");
    break;
```

## Dependencies

- Telegram Bot API (telegrambots) - Version 6.9.7.1
- Java 21

