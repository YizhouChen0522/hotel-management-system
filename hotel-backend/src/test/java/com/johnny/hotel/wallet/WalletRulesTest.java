package com.johnny.hotel.wallet;

import com.johnny.hotel.entity.*;
import com.johnny.hotel.mapper.*;
import com.johnny.hotel.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WalletRulesTest {
    final SysUserMapper users=mock(SysUserMapper.class);
    final SysRoleMapper roles=mock(SysRoleMapper.class);
    final WalletAccess access=new WalletAccess(users,roles);
    final WalletMapper wallets=mock(WalletMapper.class);
    final WalletTopUpMapper topUps=mock(WalletTopUpMapper.class);
    final WalletService service=new WalletService(wallets,topUps,mock(WalletPostingService.class),access,mock(SysAuditLogMapper.class));
    static final List<String> ROLES=List.of("CUSTOMER","STAFF","HR_ADMIN","MANAGER","OWNER","SUPER_ADMIN");
    void role(long user,String role){var r=new SysRole();r.setRoleCode(role);when(roles.selectRolesByUserId(user)).thenReturn(List.of(r));when(users.selectById(user)).thenReturn(SysUser.builder().id(user).status(1).build());}
    @BeforeEach void auth(){var auth=new UsernamePasswordAuthenticationToken("wallet-test",null,List.of());auth.setDetails(1L);SecurityContextHolder.getContext().setAuthentication(auth);role(1,"CUSTOMER");}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    static Stream<Arguments> matrix(){return ROLES.stream().flatMap(actor->ROLES.stream().map(target->Arguments.of(actor,target)));}
    @ParameterizedTest @MethodSource("matrix") void crossUserReadAndManagementMatrix(String actor,String target){
        role(1,actor);role(2,target);var wallet=Wallet.builder().id(10L).userId(2L).build();
        boolean allowed=actor.equals("SUPER_ADMIN") || Set.of("STAFF","MANAGER","OWNER").contains(actor)&&target.equals("CUSTOMER");
        if(allowed){assertDoesNotThrow(()->access.read(1L,wallet));assertDoesNotThrow(()->access.manage(1L,wallet));}
        else {assertThrows(AccessDeniedException.class,()->access.read(1L,wallet));assertThrows(AccessDeniedException.class,()->access.manage(1L,wallet));}
        assertThrows(AccessDeniedException.class,()->access.own(1L,wallet));
    }
    @ParameterizedTest @ValueSource(strings={"CUSTOMER","STAFF","HR_ADMIN","MANAGER","OWNER","SUPER_ADMIN"})
    void allRolesMayReadOwnAccountButCannotSelfReview(String role){role(1,role);var wallet=Wallet.builder().id(10L).userId(1L).build();assertEquals(1L,access.actor());assertDoesNotThrow(()->access.own(1L,wallet));assertDoesNotThrow(()->access.read(1L,wallet));assertThrows(AccessDeniedException.class,()->access.review(1L,wallet));}
    @Test void mixedCustomerEmployeeAndHrRolesStayRestricted(){role(1,"MANAGER");role(2,"CUSTOMER");var customer=new SysRole();customer.setRoleCode("CUSTOMER");var employee=new SysRole();employee.setRoleCode("STAFF");when(roles.selectRolesByUserId(2L)).thenReturn(List.of(customer,employee));var w=Wallet.builder().userId(2L).build();assertThrows(AccessDeniedException.class,()->access.read(1L,w));role(2,"CUSTOMER");var hr=new SysRole();hr.setRoleCode("HR_ADMIN");when(roles.selectRolesByUserId(1L)).thenReturn(List.of(employee,hr));assertThrows(AccessDeniedException.class,()->access.manage(1L,w));}
    @Test void disabledOrMissingJwtActorCannotAccessWallet(){when(users.selectById(1L)).thenReturn(SysUser.builder().status(0).build());assertThrows(AccessDeniedException.class,access::actor);SecurityContextHolder.clearContext();assertThrows(AccessDeniedException.class,access::actor);}
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","10000000000"})
    void amountValidationBeforeDatabaseMutation(String value){assertThrows(BusinessException.class,()->service.createTopUp(10L,WalletRequests.TopUp.builder().amount(new BigDecimal(value)).requestKey("valid_key").build()));verifyNoInteractions(wallets,topUps);}
    @Test void explicitEnumCodesAndUnknownCodes(){assertEquals(0,WalletStatus.BLOCKED.getCode());assertEquals(WalletStatus.ACTIVE,WalletStatus.fromCode(1));assertEquals(TopUpStatus.SUCCESS,TopUpStatus.fromCode(1));assertEquals(WalletTransactionType.FOLIO_PAYMENT,WalletTransactionType.fromCode(3));assertThrows(IllegalArgumentException.class,()->WalletStatus.fromCode(9));assertThrows(IllegalArgumentException.class,()->TopUpStatus.fromCode(9));assertThrows(IllegalArgumentException.class,()->WalletTransactionType.fromCode(9));}
    @Test void publicMoneyAndLedgerCrudAreAbsent(){assertFalse(Arrays.stream(WalletService.class.getMethods()).anyMatch(m->m.getName().matches(".*[Bb]alance.*|credit|debit|transfer|refund")));assertFalse(Arrays.stream(WalletPostingMapper.class.getMethods()).anyMatch(m->m.getName().startsWith("delete")||m.getName().equals("updateBalance")));assertFalse(java.lang.reflect.Modifier.isPublic(WalletPostingMapper.class.getModifiers()));}
}
