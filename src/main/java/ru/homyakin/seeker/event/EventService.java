package ru.homyakin.seeker.event;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class EventService {
    private final GetEventDao getEventDao;

    public EventService(
        GetEventDao getEventDao
    ) {
        this.getEventDao = getEventDao;
    }

    public Event getRandomEvent() {
        return getEventDao.getRandomEvent();
    }

    public Optional<Event> getEventById(Integer id) {
        return getEventDao.getById(id);
    }
}
