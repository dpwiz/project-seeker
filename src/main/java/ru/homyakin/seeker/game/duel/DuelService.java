package ru.homyakin.seeker.game.duel;

import io.vavr.control.Either;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.homyakin.seeker.game.battle.PersonageResult;
import ru.homyakin.seeker.game.battle.two_team.TwoPersonageTeamsBattle;
import ru.homyakin.seeker.game.duel.models.DuelError;
import ru.homyakin.seeker.game.duel.models.Duel;
import ru.homyakin.seeker.game.duel.models.DuelResult;
import ru.homyakin.seeker.game.duel.models.DuelStatus;
import ru.homyakin.seeker.game.personage.PersonageService;
import ru.homyakin.seeker.game.models.Money;
import ru.homyakin.seeker.game.personage.models.Personage;
import ru.homyakin.seeker.utils.TimeUtils;

@Component
public class DuelService {
    private final DuelDao duelDao;
    private final Duration duelLifeTime;
    private final PersonageService personageService;

    private final TwoPersonageTeamsBattle twoPersonageTeamsBattle;

    public DuelService(
        DuelDao duelDao,
        DuelConfig duelConfig,
        PersonageService personageService,
        TwoPersonageTeamsBattle twoPersonageTeamsBattle
    ) {
        this.duelDao = duelDao;
        this.duelLifeTime = duelConfig.lifeTime();
        this.personageService = personageService;
        this.twoPersonageTeamsBattle = twoPersonageTeamsBattle;
    }

    //TODO прочитать про transactional
    public Either<DuelError, Duel> createDuel(
        Personage initiatingPersonage,
        Personage acceptingPersonage,
        long groupId
    ) {
        if (duelDao.getWaitingDuelByInitiatingPersonage(initiatingPersonage.id()).isPresent()) {
            return Either.left(new DuelError.PersonageAlreadyHasDuel());
        }
        if (initiatingPersonage.money().lessThan(DUEL_PRICE)) {
            return Either.left(new DuelError.InitiatingPersonageNotEnoughMoney(DUEL_PRICE));
        }

        personageService.takeMoney(initiatingPersonage, DUEL_PRICE);

        final var id = duelDao.create(initiatingPersonage.id(), acceptingPersonage.id(), groupId, duelLifeTime);
        return Either.right(getByIdForce(id));
    }

    public Duel getByIdForce(long duelId) {
        return duelDao.getById(duelId)
            .orElseThrow(() -> new IllegalStateException("Duel " + duelId + "must exist"));
    }

    public void addMessageIdToDuel(long duelId, int messageId) {
        duelDao.addMessageIdToDuel(duelId, messageId);
    }

    public List<Duel> getExpiringDuels() {
        return duelDao.getWaitingDuelsWithLessExpireDate(TimeUtils.moscowTime());
    }

    public void expireDuel(long duelId) {
        //TODO проверка на то, что статус был вейтинг
        returnMoneyToInitiator(duelId);
        duelDao.updateStatus(duelId, DuelStatus.EXPIRED);
    }

    public void declineDuel(long duelId) {
        //TODO проверка на то, что статус был не финишд
        returnMoneyToInitiator(duelId);
        duelDao.updateStatus(duelId, DuelStatus.DECLINED);
    }

    public DuelResult finishDuel(Duel duel) {
        //TODO проверка на то, что статус был не финишд
        duelDao.updateStatus(duel.id(), DuelStatus.FINISHED);
        final var personage1 = personageService.getByIdForce(duel.initiatingPersonageId());
        final var personage2 = personageService.getByIdForce(duel.acceptingPersonageId());
        final var battleResult = twoPersonageTeamsBattle.battle(
            List.of(personage1),
            List.of(personage2)
        );

        final PersonageResult winner;
        final PersonageResult looser;
        switch (battleResult.winner()) {
            case FIRST_TEAM -> {
                winner = battleResult.firstTeamResult().personageResults().get(0);
                looser = battleResult.secondTeamResult().personageResults().get(0);
            }
            case SECOND_TEAM -> {
                winner = battleResult.secondTeamResult().personageResults().get(0);
                looser = battleResult.firstTeamResult().personageResults().get(0);
            }
            default -> throw new IllegalStateException("Unexpected status");
        }
        duelDao.addWinnerIdToDuel(duel.id(), winner.personage().id());
        return new DuelResult(winner, looser);
    }

    private void returnMoneyToInitiator(long duelId) {
        final var initiatingPersonage = personageService.getByIdForce(getByIdForce(duelId).initiatingPersonageId());
        personageService.addMoney(initiatingPersonage, DUEL_PRICE);
    }

    private static final Money DUEL_PRICE = new Money(3);
}
