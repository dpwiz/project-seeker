package ru.homyakin.seeker.command.chat_action;

import ru.homyakin.seeker.command.Command;

public record LeftChat(
    Long chatId
) implements Command {
}
