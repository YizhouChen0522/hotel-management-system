package com.johnny.hotel.wallet;

import com.johnny.hotel.booking.deposit.DepositRequests;
import com.johnny.hotel.dto.ApproveBookingRequest;
import com.johnny.hotel.exception.BusinessException;
import com.johnny.hotel.group.*;
import com.johnny.hotel.guest.GuestRequests;
import com.johnny.hotel.reservation.ReservationLifecycleService;
import com.johnny.hotel.reservation.ReservationPolicyRequests;
import com.johnny.hotel.reservation.ReservationPolicyService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class GroupReservationDevelopmentTest extends FinancialDevelopmentFixture {
    @Autowired GroupReservationService groups;
    @Autowired GroupMapper groupDb;
    @Autowired com.johnny.hotel.booking.deposit.DepositService deposits;
    @Autowired ReservationLifecycleService lifecycle;
    @Autowired ReservationPolicyService policyService;

    private final List<Long> groupIds=new ArrayList<>();
    private final List<Long> groupGuestIds=new ArrayList<>();

    @BeforeEach void activePolicy(){
        as("OWNER");
        var band=ReservationPolicyRequests.Band.builder().minLeadDays(0).refundPercent(new BigDecimal("100.00")).build();
        var policy=policyService.create(ReservationPolicyRequests.Save.builder().name(run+" group policy").bands(List.of(band)).build());
        policyService.activate(policy.policy().getId());
    }

    private GroupModels.Group group(String suffix){
        as("MANAGER");
        var r=new GroupRequests.Create();r.setGroupCode(run+suffix+UUID.randomUUID().toString().substring(0,6));
        r.setGroupName("Acceptance "+suffix);r.setGroupType("LEISURE");r.setCurrency("CAD");
        r.setBookingSource("GROUP_DIRECT");r.setArrivalDate(arrival);r.setDepartureDate(arrival.plusDays(3));
        var g=groups.create(r);groupIds.add(g.getId());return g;
    }
    private GroupModels.Block block(long group,int quantity){
        as("MANAGER");var r=new GroupRequests.Block();r.setRoomTypeId(type1);r.setStartDate(arrival);
        r.setEndDate(arrival.plusDays(3));r.setQuantity(quantity);r.setRateMode("NEGOTIATED_RATE");
        r.setNegotiatedRate(new BigDecimal("100.00"));return groups.addBlock(group,r);
    }
    private GroupModels.Entry entry(long group,long block,String suffix){
        as("STAFF");var profile=guests.create(GuestRequests.Profile.builder().firstName("Group").lastName("Guest "+suffix)
                .nationality("CA").documentType("PASSPORT").documentNumber(run+suffix+UUID.randomUUID().toString().substring(0,6)).build(),uid("STAFF"));
        groupGuestIds.add(profile.id());var r=new GroupRequests.Entry();r.setBlockId(block);r.setGuestProfileId(profile.id());r.setRequestKey(run+suffix+UUID.randomUUID().toString().substring(0,6));
        return groups.addEntry(group,r);
    }
    private GroupModels.Group confirmed(String suffix,int quantity){var g=group(suffix);block(g.getId(),quantity);as("MANAGER");return groups.confirm(g.getId());}
    private Throwable concurrent(String role,Callable<?> work,CountDownLatch start){
        try{start.await(10,TimeUnit.SECONDS);as(role);work.call();return null;}catch(Throwable x){return x;}finally{SecurityContextHolder.clearContext();}
    }
    private List<Throwable> race(String roleA,Callable<?> a,String roleB,Callable<?> b)throws Exception{
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var x=pool.submit(()->concurrent(roleA,a,start));var y=pool.submit(()->concurrent(roleB,b,start));start.countDown();return Arrays.asList(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
    }
    private long child(long group){return jdbc.queryForObject("SELECT id FROM booking WHERE group_id=? ORDER BY id DESC LIMIT 1",Long.class,group);}

    @AfterEach void detachAndRemoveGroups(){
        for(long group:groupIds){
            bookingIds.addAll(jdbc.queryForList("SELECT id FROM booking WHERE group_id=?",Long.class,group));
            jdbc.update("UPDATE booking SET group_id=NULL,group_block_id=NULL WHERE group_id=?",group);
            jdbc.update("DELETE FROM group_rooming_entry WHERE group_id=?",group);
            jdbc.update("DELETE FROM group_room_block WHERE group_id=?",group);
            jdbc.update("DELETE FROM group_reservation WHERE id=?",group);
        }
        for(long guest:groupGuestIds)jdbc.update("DELETE FROM guest_profile WHERE id=? AND NOT EXISTS(SELECT 1 FROM booking_guest WHERE guest_id=?)",guest,guest);
    }

    @Test void realMysqlLastSlotPickupAndDuplicateRequestAreExactlyOnce()throws Exception{
        var g=confirmed("last",1);var b=groupDb.blocks(g.getId()).get(0);var e1=entry(g.getId(),b.getId(),"a");var e2=entry(g.getId(),b.getId(),"b");
        var result=race("STAFF",()->groups.pickup(e1.getId()),"STAFF",()->groups.pickup(e2.getId()));
        assertEquals(1,result.stream().filter(Objects::isNull).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE group_id=?",Integer.class,g.getId()));
        var after=groupDb.block(b.getId());assertEquals(1,after.getPickedUpQuantity());assertEquals(0,after.remaining());
        var picked=jdbc.queryForObject("SELECT id FROM group_rooming_entry WHERE group_id=? AND booking_id IS NOT NULL",Long.class,g.getId());
        var replay=race("STAFF",()->groups.pickup(picked),"STAFF",()->groups.pickup(picked));
        assertEquals(2,replay.stream().filter(Objects::isNull).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE group_id=?",Integer.class,g.getId()));
    }

    @Test void realMysqlPickupVsReleaseCannotDoubleConsumeSlot()throws Exception{
        var g=confirmed("release",1);var b=groupDb.blocks(g.getId()).get(0);var e=entry(g.getId(),b.getId(),"r");
        var result=race("STAFF",()->groups.pickup(e.getId()),"MANAGER",()->groups.release(b.getId(),1));
        assertEquals(1,result.stream().filter(Objects::isNull).count());
        var after=groupDb.block(b.getId());assertEquals(1,after.getPickedUpQuantity()+after.getReleasedQuantity());assertEquals(0,after.remaining());
    }

    @Test void realMysqlNormalBookingAndTwoGroupsShareLastCapacity()throws Exception{
        long existing=createBooking("CUSTOMER");var approve=new ApproveBookingRequest();approve.setAssignedRoomId(room1);bookings.approveBooking(existing,approve,uid("MANAGER"));
        long pending=createBooking("OTHER_CUSTOMER");var approveSecond=new ApproveBookingRequest();approveSecond.setAssignedRoomId(room2);
        var g=group("normal-race");block(g.getId(),1);
        var normalVsGroup=race("MANAGER",()->bookings.approveBooking(pending,approveSecond,uid("MANAGER")),"MANAGER",()->groups.confirm(g.getId()));
        assertEquals(1,normalVsGroup.stream().filter(Objects::isNull).count());
        int approved=jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE id=? AND status=1",Integer.class,pending);
        int confirmed=jdbc.queryForObject("SELECT COUNT(*) FROM group_reservation WHERE id=? AND status=2",Integer.class,g.getId());
        assertEquals(1,approved+confirmed);
    }

    @Test void realMysqlTwoGroupsCannotBothTakeLastCapacity()throws Exception{
        long existing=createBooking("CUSTOMER");var approve=new ApproveBookingRequest();approve.setAssignedRoomId(room1);bookings.approveBooking(existing,approve,uid("MANAGER"));
        var a=group("ga");var c=group("gb");block(a.getId(),1);block(c.getId(),1);
        var result=race("MANAGER",()->groups.confirm(a.getId()),"MANAGER",()->groups.confirm(c.getId()));
        assertEquals(1,result.stream().filter(Objects::isNull).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM group_reservation WHERE id IN (?,?) AND status=2",Integer.class,a.getId(),c.getId()));
    }

    @Test void capacityFormulaAndPagedReadUseCommittedBlockNotPickupTwice(){
        var g=confirmed("formula",2);var b=groupDb.blocks(g.getId()).get(0);var e=entry(g.getId(),b.getId(),"p");as("STAFF");groups.pickup(e.getId());
        assertEquals(2,groupDb.groupCommitted(type1,arrival,arrival.plusDays(1)));
        assertEquals(1,groupDb.block(b.getId()).remaining());
        var rooming=groups.entries(g.getId(),2,b.getId(),1,1);assertEquals(1,rooming.getTotal());assertEquals(1,rooming.getItems().size());
        var children=groups.children(g.getId(),0,1,1);assertEquals(1,children.getTotal());assertEquals(1,children.getItems().size());
        as("MANAGER");groups.release(b.getId(),1);assertEquals(1,groupDb.groupCommitted(type1,arrival.plusDays(2),arrival.plusDays(3)));
        for(String action:List.of("GROUP_CREATE","GROUP_BLOCK_CREATE","GROUP_CONFIRM","GROUP_ROOMING_CREATE","GROUP_PICKUP","GROUP_BLOCK_RELEASE"))
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log WHERE operator_id IN (?,?,?) AND action=?",Integer.class,uid("STAFF"),uid("MANAGER"),uid("OWNER"),action)>0,action);
    }

    @Test void cancellationAfterGroupCancelMovesPickedSlotToReleasedExactlyOnce(){
        var g=confirmed("cancelled-group",2);var b=groupDb.blocks(g.getId()).get(0);var e=entry(g.getId(),b.getId(),"cg");as("STAFF");groups.pickup(e.getId());long booking=child(g.getId());
        as("MANAGER");groups.cancel(g.getId());var first=lifecycle.cancel(booking,uid("MANAGER"),"HOTEL","Cancelled group attendee");var replay=lifecycle.cancel(booking,uid("MANAGER"),"HOTEL","Cancelled group attendee");
        assertEquals(first.getId(),replay.getId());var after=groupDb.block(b.getId());assertEquals(0,after.getPickedUpQuantity());assertEquals(2,after.getReleasedQuantity());assertEquals(0,after.remaining());
    }

    @Test void childCancellationReturnsSlotAndFullLifecycleUsesOrdinaryAuthorities(){
        var g=confirmed("life",2);var b=groupDb.blocks(g.getId()).get(0);
        var cancelledEntry=entry(g.getId(),b.getId(),"cancel");as("STAFF");groups.pickup(cancelledEntry.getId());long cancelled=child(g.getId());
        as("MANAGER");var first=lifecycle.cancel(cancelled,uid("MANAGER"),"HOTEL","Group attendee cancelled");var replay=lifecycle.cancel(cancelled,uid("MANAGER"),"HOTEL","Group attendee cancelled");
        assertEquals(first.getId(),replay.getId());assertEquals(0,groupDb.block(b.getId()).getPickedUpQuantity());assertEquals(2,groupDb.block(b.getId()).remaining());

        var liveEntry=entry(g.getId(),b.getId(),"live");as("STAFF");groups.pickup(liveEntry.getId());long booking=child(g.getId());
        as("STAFF");deposits.receive(booking,DepositRequests.Receive.builder().amount(new BigDecimal("300.00")).paymentMethod("CARD").referenceNo(run+"card").requestKey(run+"deposit").build());
        var approve=new ApproveBookingRequest();approve.setAssignedRoomId(room1);bookings.approveBooking(booking,approve,uid("MANAGER"));guests.confirm(booking,uid("MANAGER"));bookings.checkIn(booking,uid("MANAGER"));
        assertNotNull(jdbc.queryForObject("SELECT id FROM stay WHERE booking_id=?",Long.class,booking));assertNotNull(folio(booking));
        assertEquals(new BigDecimal("300.00"),jdbc.queryForObject("SELECT total_price FROM booking WHERE id=?",BigDecimal.class,booking));
        clock.day(3);bookings.checkOut(booking,uid("MANAGER"));
        assertEquals(2,jdbc.queryForObject("SELECT status FROM stay WHERE booking_id=?",Integer.class,booking));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM folio f JOIN stay s ON s.id=f.stay_id WHERE s.booking_id=? AND f.closed_time IS NULL",Integer.class,booking));
    }
}


