package com.johnny.hotel;import com.johnny.hotel.pagination.*;import com.johnny.hotel.exception.BusinessException;import org.junit.jupiter.api.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class PaginationSupportTest{
 PaginationProperties p;PaginationSupport support;@BeforeEach void set(){p=new PaginationProperties();p.setMaxPageSize(5);p.setBrowseLimit(7);support=new PaginationSupport(p);}
 @Test void defaultsAndMetadata(){var w=support.window(null,null,false);var r=support.result(w,List.of(1,2),7);assertEquals(1,r.getPage());assertEquals(5,r.getPageSize());assertTrue(r.isHasNext());assertFalse(r.isSearchMode());}
 @Test void rejectsInvalidPage(){assertThrows(BusinessException.class,()->support.window(0,1,false));assertThrows(BusinessException.class,()->support.window(-1,1,false));}
 @Test void rejectsInvalidSize(){assertThrows(BusinessException.class,()->support.window(1,0,false));assertThrows(BusinessException.class,()->support.window(1,6,false));}
 @Test void configuredMaximumChangesWithoutCode(){p.setMaxPageSize(2);assertThrows(BusinessException.class,()->support.window(1,3,true));assertEquals(2,support.window(1,2,true).pageSize());}
 @Test void browseTotalAndLimitAreCapped(){var w=support.window(3,3,false);assertEquals(1,support.limit(w));var r=support.result(w,List.of(1),1000);assertEquals(7,r.getTotal());assertFalse(r.isHasNext());}
 @Test void browseCannotCrossBoundary(){var w=support.window(4,3,false);assertEquals(0,support.limit(w));assertEquals(7,support.result(w,List.of(),1000).getTotal());}
 @Test void searchIsNotBrowseCapped(){var w=support.window(20,5,true);assertEquals(5,support.limit(w));assertEquals(500,support.result(w,List.of(1,2,3,4,5),500).getTotal());}
}
