package com.johnny.hotel.service;

import com.johnny.hotel.dto.FolioItemCommand;
import com.johnny.hotel.dto.RoomChangeBillingCommand;
import com.johnny.hotel.entity.Folio;
import com.johnny.hotel.entity.FolioItem;

import java.util.List;

public interface FolioService {

    Folio ensureFolioExists(Long stayId);

    FolioItem addItem(Long stayId, FolioItemCommand command, Long operatorId);

    List<FolioItem> addItems(Long stayId, List<FolioItemCommand> commands, Long operatorId);

    void applyRoomChangeBilling(RoomChangeBillingCommand command
    );
}
