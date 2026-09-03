package com.johnny.hotel.service;

import com.johnny.hotel.entity.Folio;

public interface FolioFinancialService {

    Folio recalculateSummary(Long bookingId);
}
