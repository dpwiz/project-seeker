package ru.homyakin.seeker.telegram.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.homyakin.seeker.game.event.models.EventResult;
import ru.homyakin.seeker.game.event.service.EventProcessing;
import ru.homyakin.seeker.game.event.service.GroupEventService;
import ru.homyakin.seeker.infrastructure.lock.LockPrefixes;
import ru.homyakin.seeker.infrastructure.lock.LockService;
import ru.homyakin.seeker.locale.personal.PersonalQuestLocalization;
import ru.homyakin.seeker.locale.raid.RaidLocalization;
import ru.homyakin.seeker.telegram.group.stats.GroupStatsService;
import ru.homyakin.seeker.telegram.group.GroupTgService;
import ru.homyakin.seeker.game.event.launched.LaunchedEvent;
import ru.homyakin.seeker.telegram.TelegramSender;
import ru.homyakin.seeker.telegram.user.UserService;
import ru.homyakin.seeker.telegram.utils.EditMessageTextBuilder;
import ru.homyakin.seeker.telegram.utils.SendMessageBuilder;

@Service
public class TgEventStopper {
    private static final Logger logger = LoggerFactory.getLogger(TgEventStopper.class);
    private final GroupTgService groupTgService;
    private final TelegramSender telegramSender;
    private final GroupEventService groupEventService;
    private final EventProcessing eventProcessing;
    private final GroupStatsService groupStatsService;
    private final LockService lockService;
    private final UserService userService;

    public TgEventStopper(
        GroupTgService groupTgService,
        TelegramSender telegramSender,
        GroupEventService groupEventService,
        EventProcessing eventProcessing,
        GroupStatsService groupStatsService,
        LockService lockService,
        UserService userService
    ) {
        this.groupTgService = groupTgService;
        this.telegramSender = telegramSender;
        this.groupEventService = groupEventService;
        this.eventProcessing = eventProcessing;
        this.groupStatsService = groupStatsService;
        this.lockService = lockService;
        this.userService = userService;
    }

    public void stopEvents() {
        groupEventService
            .getExpiredActiveEvents()
            .forEach(event -> {
                final var key = LockPrefixes.LAUNCHED_EVENT.name() + event.id();
                lockService.tryLockAndExecute(key, () -> stopLaunchedEvent(event));
            });
    }

    private void stopLaunchedEvent(LaunchedEvent launchedEvent) {
        logger.info("Stopping event " + launchedEvent.id());
        final var result = eventProcessing.processEvent(launchedEvent);
        switch (result) {
            case EventResult.RaidResult raidResult -> processRaidResult(launchedEvent, raidResult);
            case EventResult.PersonalQuestResult personalQuestResult -> processPersonalQuestResult(personalQuestResult);
        }
    }

    private void processPersonalQuestResult(EventResult.PersonalQuestResult result) {
        final var message = SendMessageBuilder.builder();
        switch (result) {
            case EventResult.PersonalQuestResult.Error _ -> {
                return;
            }
            case EventResult.PersonalQuestResult.Failure failure -> {
                final var user = userService.getByPersonageIdForce(failure.personage().id());
                message.chatId(user.id());
                message.text(PersonalQuestLocalization.failedQuest(user.language(), failure));
            }
            case EventResult.PersonalQuestResult.Success success -> {
                final var user = userService.getByPersonageIdForce(success.personage().id());
                message.chatId(user.id());
                message.text(PersonalQuestLocalization.successQuest(user.language(), success));
            }
        }
        telegramSender.send(message.build());
    }

    private void processRaidResult(LaunchedEvent launchedEvent, EventResult.RaidResult result) {
        groupEventService.getByLaunchedEventId(launchedEvent.id())
            .forEach(
                groupEvent -> {
                    final var group = groupTgService.getOrCreate(groupEvent.groupId());
                    switch (result) {
                        case EventResult.RaidResult.Completed completed -> {
                            groupStatsService.updateRaidStats(group.id(), completed);
                            telegramSender.send(EditMessageTextBuilder.builder()
                                .chatId(groupEvent.groupId())
                                .messageId(groupEvent.messageId())
                                .text(completed.raid().toEndMessage(completed, group.language()))
                                .build()
                            );
                            telegramSender.send(SendMessageBuilder.builder()
                                .chatId(groupEvent.groupId())
                                .text(completed.raid().endMessage(group.language(), completed))
                                .replyMessageId(groupEvent.messageId())
                                .build()
                            );
                        }
                        case EventResult.RaidResult.Expired _ -> telegramSender.send(
                            EditMessageTextBuilder.builder()
                                .chatId(groupEvent.groupId())
                                .messageId(groupEvent.messageId())
                                .text(RaidLocalization.zeroParticipants(group.language()))
                                .build()
                        );
                    }
                }
            );
    }
}