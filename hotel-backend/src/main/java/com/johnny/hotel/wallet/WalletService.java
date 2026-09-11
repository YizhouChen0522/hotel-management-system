package com.johnny.hotel.wallet;

import com.johnny.hotel.entity.SysAuditLog;
import com.johnny.hotel.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static com.johnny.hotel.service.support.BillingRules.*;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletMapper wallets;
    private final WalletTopUpMapper requests;
    private final WalletPostingService posting;
    private final WalletAccess access;
    private final SysAuditLogMapper audits;

    public WalletViews.Account mine() {return byUser(access.actor());}
    public WalletViews.Account byUser(Long userId) {
        Long actor=access.actor();var wallet=wallets.byUser(userId);access.read(actor,wallet);return WalletViews.Account.from(wallet);
    }
    public WalletViews.Account get(Long id) {
        Long actor=access.actor();var wallet=wallets.find(id);access.read(actor,wallet);return WalletViews.Account.from(wallet);
    }
    public List<WalletViews.Transaction> transactions(Long id,long after) {
        require(after>=0,"Invalid cursor");access.read(access.actor(),wallets.find(id));
        return wallets.transactions(id,after).stream().map(WalletViews.Transaction::from).toList();
    }
    public List<WalletViews.TopUp> topUps(Long id,long after) {
        require(after>=0,"Invalid cursor");access.read(access.actor(),wallets.find(id));
        return requests.list(id,after).stream().map(WalletViews.TopUp::from).toList();
    }
    private void key(String key) {require(key!=null && key.matches("[A-Za-z0-9_-]{8,64}"),"Invalid request key");}
    private String reason(String reason) {require(reason!=null && !reason.isBlank() && reason.length()<=255,"A reason of up to 255 characters is required");return reason.trim();}
    private void audit(Long actor,Wallet wallet,String action,String detail) {
        one(audits.insert(SysAuditLog.builder().operatorId(actor).targetUserId(wallet.getUserId()).action(action).detail(detail).build()));
    }

    @Transactional
    public WalletViews.TopUp createTopUp(Long id,WalletRequests.TopUp request) {
        Long actor=access.actor();require(request!=null,"Top-up request is required");key(request.getRequestKey());
        var amount=money(request.getAmount(),12);require(amount.signum()>0,"Amount must be positive");
        // All writers acquire Wallet before TopUp; never the inverse. MySQL is the only idempotency authority.
        var wallet=wallets.lock(id);access.own(actor,wallet);
        var existing=requests.byKey(id,request.getRequestKey());
        if(existing!=null) {require(existing.getAmount().compareTo(amount)==0,"Request key reused with a different amount");return WalletViews.TopUp.from(existing);}
        require(WalletStatus.fromCode(wallet.getStatus())==WalletStatus.ACTIVE,"Wallet top-ups are blocked");
        var topUp=WalletTopUp.builder().walletId(id).amount(amount).requestKey(request.getRequestKey()).requestedBy(actor).build();
        one(requests.insert(topUp));audit(actor,wallet,"WALLET_TOP_UP_REQUEST","Top-up "+topUp.getId());
        return WalletViews.TopUp.from(requests.lock(id,topUp.getId()));
    }
    @Transactional
    public WalletViews.TopUp confirm(Long id,Long topUpId,WalletRequests.Decision request) {return resolve(id,topUpId,request,true);}
    @Transactional
    public WalletViews.TopUp reject(Long id,Long topUpId,WalletRequests.Decision request) {return resolve(id,topUpId,request,false);}
    private WalletViews.TopUp resolve(Long id,Long topUpId,WalletRequests.Decision decision,boolean confirm) {
        Long actor=access.actor();require(decision!=null,"Decision is required");key(decision.getRequestKey());String reason=reason(decision.getReason());
        var wallet=wallets.lock(id);access.review(actor,wallet);
        var topUp=requests.lock(id,topUpId);require(topUp!=null,"Top-up does not belong to this wallet");
        var target=confirm?TopUpStatus.SUCCESS:TopUpStatus.REJECTED;
        if(TopUpStatus.fromCode(topUp.getStatus())!=TopUpStatus.PENDING) {
            require(topUp.getStatus()==target.getCode() && decision.getRequestKey().equals(topUp.getResolutionKey())
                    && reason.equals(topUp.getReason()),"Top-up already resolved with another decision");
            return WalletViews.TopUp.from(topUp);
        }
        if(confirm) {
            require(WalletStatus.fromCode(wallet.getStatus())==WalletStatus.ACTIVE,"Wallet top-ups are blocked");
            posting.creditTopUp(wallet,topUp,actor,decision.getRequestKey());
        }
        one(requests.resolve(topUpId,target.getCode(),decision.getRequestKey(),actor,reason));
        audit(actor,wallet,confirm?"WALLET_TOP_UP_CONFIRM":"WALLET_TOP_UP_REJECT","Top-up "+topUpId+": "+reason);
        return WalletViews.TopUp.from(requests.lock(id,topUpId));
    }
    @Transactional
    public WalletViews.Account status(Long id,WalletRequests.Status request) {
        Long actor=access.actor();require(request!=null && request.getStatus()!=null,"Wallet status is required");String reason=reason(request.getReason());
        var wallet=wallets.lock(id);access.manage(actor,wallet);
        if(wallet.getStatus()!=request.getStatus().getCode()) {
            one(wallets.status(id,request.getStatus().getCode()));
            audit(actor,wallet,"WALLET_STATUS",request.getStatus()+": "+reason);
        }
        return WalletViews.Account.from(wallets.lock(id));
    }
}
