package ru.homyakin.seeker.telegram.command.group.duel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.homyakin.seeker.game.duel.DuelService;
import ru.homyakin.seeker.game.duel.models.DuelResult;
import ru.homyakin.seeker.game.duel.models.DuelStatus;
import ru.homyakin.seeker.locale.Language;
import ru.homyakin.seeker.locale.duel.DuelLocalization;
import ru.homyakin.seeker.telegram.TelegramSender;
import ru.homyakin.seeker.telegram.command.CommandExecutor;
import ru.homyakin.seeker.telegram.group.GroupStatsService;
import ru.homyakin.seeker.telegram.group.GroupUserService;
import ru.homyakin.seeker.telegram.models.TgPersonageMention;
import ru.homyakin.seeker.telegram.user.UserService;
import ru.homyakin.seeker.telegram.user.models.User;
import ru.homyakin.seeker.telegram.utils.EditMessageTextBuilder;
import ru.homyakin.seeker.telegram.utils.TelegramMethods;

@Component
public class AcceptDuelExecutor extends CommandExecutor<AcceptDuel> {
    private static final Logger logger = LoggerFactory.getLogger(AcceptDuelExecutor.class);
    private final GroupUserService groupUserService;
    private final DuelService duelService;
    private final TelegramSender telegramSender;
    private final GroupStatsService groupStatsService;
    private final UserService userService;

    public AcceptDuelExecutor(
        GroupUserService groupUserService,
        DuelService duelService,
        TelegramSender telegramSender,
        GroupStatsService groupStatsService,
        UserService userService
    ) {
        this.groupUserService = groupUserService;
        this.duelService = duelService;
        this.telegramSender = telegramSender;
        this.groupStatsService = groupStatsService;
        this.userService = userService;
    }

    @Override
    public void execute(AcceptDuel command) {
        final var groupUser = groupUserService.getAndActivateOrCreate(command.groupId(), command.userId());
        final var duel = duelService.getByIdForce(command.duelId());
        final var acceptingUser = groupUser.second();
        final var group = groupUser.first();

        if (duel.acceptingPersonageId() != acceptingUser.personageId()) {
            telegramSender.send(
                TelegramMethods.createAnswerCallbackQuery(
                    command.callbackId(),
                    DuelLocalization.notDuelAcceptingPersonage(group.language())
                )
            );
            return;
        }

        if (duel.status() != DuelStatus.WAITING) {
            //TODO нормальный обработчик
            return;
        }

        final var result = duelService.finishDuel(duel);
        final User winnerUser;
        final User looserUser;
        if (result.winner().personage().id() == acceptingUser.personageId()) {
            winnerUser = acceptingUser;
            looserUser = userService.getByPersonageIdForce(result.looser().personage().id());
        } else {
            winnerUser = userService.getByPersonageIdForce(result.winner().personage().id());
            looserUser = acceptingUser;
        }
        groupStatsService.increaseDuelsComplete(command.groupId(), 1);

        telegramSender.send(
            EditMessageTextBuilder.builder()
                .chatId(group.id())
                .messageId(command.messageId())
                .text(finishedDuelText(group.language(), result, winnerUser, looserUser))
                .build()
        );
    }

    private String finishedDuelText(
        Language language,
        DuelResult duelResult,
        User winnerUser,
        User looserUser
    ) {
        return DuelLocalization.finishedDuel(
            language,
            TgPersonageMention.of(duelResult.winner().personage(), winnerUser.id()),
            TgPersonageMention.of(duelResult.looser().personage(), looserUser.id())
        ) + "\n\n" + duelResult.winner().statsText(language) + "\n" + duelResult.looser().statsText(language);
    }
}
