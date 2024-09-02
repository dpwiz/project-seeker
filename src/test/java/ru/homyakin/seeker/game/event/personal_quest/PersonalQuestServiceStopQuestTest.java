package ru.homyakin.seeker.game.event.personal_quest;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.homyakin.seeker.game.event.models.EventResult;
import ru.homyakin.seeker.game.event.models.LaunchedEvent;
import ru.homyakin.seeker.game.event.personal_quest.model.PersonalQuest;
import ru.homyakin.seeker.game.event.service.LaunchedEventService;
import ru.homyakin.seeker.game.models.Money;
import ru.homyakin.seeker.game.personage.PersonageService;
import ru.homyakin.seeker.infrastructure.lock.InMemoryLockService;
import ru.homyakin.seeker.infrastructure.lock.LockService;
import ru.homyakin.seeker.test_utils.PersonageUtils;
import ru.homyakin.seeker.test_utils.event.LaunchedEventUtils;
import ru.homyakin.seeker.utils.RandomUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class PersonalQuestServiceStopQuestTest {
    private final PersonalQuestDao personalQuestDao = Mockito.mock(PersonalQuestDao.class);
    private final PersonageService personageService = Mockito.mock(PersonageService.class);
    private final LockService lockService = new InMemoryLockService();
    private final LaunchedEventService launchedEventService = Mockito.mock(LaunchedEventService.class);
    private final PersonalQuestConfig config = Mockito.mock(PersonalQuestConfig.class);
    private final PersonalQuestService personalQuestService = new PersonalQuestService(
        personalQuestDao,
        personageService,
        lockService,
        launchedEventService,
        config
    );

    @Test
    public void Given_LaunchedEvent_When_RandomIsSuccess_Then_ReturnSuccess() {
        // given
        final var personage = PersonageUtils.random();

        // when
        final EventResult.PersonalQuestResult result;
        try (final var mock = Mockito.mockStatic(RandomUtils.class)) {
            mock.when(() -> RandomUtils.processChance(config.successProbability())).thenReturn(true);
            mock.when(() -> RandomUtils.getInInterval(config.reward())).thenReturn(REWARD.value());
            when(personalQuestDao.getByEventId(launchedEvent.eventId())).thenReturn(Optional.of(quest));
            when(personageService.getByLaunchedEvent(launchedEvent.id())).thenReturn(List.of(personage));

            result = personalQuestService.stopQuest(launchedEvent);
        }

        // then
        final var expected = new EventResult.PersonalQuestResult.Success(quest, personage, REWARD);
        assertEquals(expected, result);
    }

    @Test
    public void Given_LaunchedEvent_When_RandomIsNotSuccess_Then_ReturnFailure() {
        // given
        final var personage = PersonageUtils.random();

        // when
        final EventResult.PersonalQuestResult result;
        try (final var mock = Mockito.mockStatic(RandomUtils.class)) {
            mock.when(() -> RandomUtils.processChance(config.successProbability())).thenReturn(false);
            mock.when(() -> RandomUtils.getInInterval(config.reward())).thenReturn(REWARD.value());
            when(personalQuestDao.getByEventId(launchedEvent.eventId())).thenReturn(Optional.of(quest));
            when(personageService.getByLaunchedEvent(launchedEvent.id())).thenReturn(List.of(personage));

            result = personalQuestService.stopQuest(launchedEvent);
        }

        // then
        final var expected = new EventResult.PersonalQuestResult.Failure(quest, personage);
        assertEquals(expected, result);
    }

    @Test
    public void When_EventIsNotQuest_Then_ThrowException() {
        // when
        when(personalQuestDao.getByEventId(launchedEvent.eventId())).thenReturn(Optional.empty());
        final var exception = assertThrows(
            IllegalStateException.class,
            () -> personalQuestService.stopQuest(launchedEvent)
        );

        // then
        assertEquals("Event " + launchedEvent.eventId() + " is not quest", exception.getMessage());
    }

    @Test
    public void When_PersonagesCountIsNotOne_Then_ReturnError() {
        // given
        final var personage1 = PersonageUtils.random();
        final var personage2 = PersonageUtils.random();

        when(personalQuestDao.getByEventId(launchedEvent.eventId())).thenReturn(Optional.of(quest));
        when(personageService.getByLaunchedEvent(launchedEvent.id())).thenReturn(List.of(personage1, personage2));

        // when
        final var result = personalQuestService.stopQuest(launchedEvent);

        // then
        assertEquals(EventResult.PersonalQuestResult.Error.INSTANCE, result);
    }

    private final LaunchedEvent launchedEvent = LaunchedEventUtils.withEventId(1);
    private final PersonalQuest quest = new PersonalQuest(launchedEvent.eventId(), "Quest Name", Collections.emptyMap());
    private final Money REWARD = new Money(1000);
}