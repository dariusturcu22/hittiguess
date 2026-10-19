package org.dariusturcu.backend.history;

import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.SessionResultsDTO;

public record GameCompleted(GameSession session, SessionResultsDTO results, String endingReason) {}
