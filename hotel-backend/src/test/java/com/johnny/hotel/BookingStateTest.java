package com.johnny.hotel;
import com.johnny.hotel.entity.Booking;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.service.impl.BookingServiceImpl;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingStateTest {
    @Mock BookingMapper mapper;@Mock RoomMapper rooms;@Mock SysAuditLogMapper audits;
    @InjectMocks BookingServiceImpl service;
    void booking(int state){var b=new Booking();b.setId(1L);b.setUserId(1L);b.setStatus(state);when(mapper.selectByIdForUpdate(1L)).thenReturn(b);}
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5}) void rejectionOnlyPending(int state){booking(state);assertThrows(BusinessException.class,()->service.rejectBooking(1L,2L));verifyNoInteractions(rooms,audits);}
    @ParameterizedTest @ValueSource(ints={2,3,4,5}) void cancelOnlyPendingOrApproved(int state){booking(state);assertThrows(BusinessException.class,()->service.cancelBooking(1L,1L));verifyNoInteractions(rooms,audits);}
    @ParameterizedTest @ValueSource(ints={0,1,3,4,5}) void checkoutOnlyCheckedIn(int state){booking(state);assertThrows(BusinessException.class,()->service.checkOut(1L,2L));verifyNoInteractions(rooms,audits);}
}
