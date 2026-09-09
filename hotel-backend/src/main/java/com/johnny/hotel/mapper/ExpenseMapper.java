package com.johnny.hotel.mapper;
import com.johnny.hotel.entity.ExpenseRegistration;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ExpenseMapper {
    @Select("SELECT * FROM expense_registration WHERE folio_id=#{folioId} ORDER BY id FOR UPDATE")
    List<ExpenseRegistration> selectByFolioForUpdate(Long folioId);
    @Insert("""
        INSERT INTO expense_registration(folio_id,request_key,item_type,amount,business_date,description,reason,
            source_expense_id,status,registered_by,registered_time)
        VALUES(#{folioId},#{requestKey},#{itemType},#{amount},#{businessDate},#{description},#{reason},
            #{sourceExpenseId},'PENDING',#{registeredBy},#{registeredTime})
        """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(ExpenseRegistration e);
    @Update("""
        UPDATE expense_registration SET status=#{status},ledger_item_id=#{ledgerItemId},resolved_by=#{resolvedBy},
            resolved_time=#{resolvedTime},cancel_key=#{cancelKey},cancel_reason=#{cancelReason}
        WHERE id=#{id} AND folio_id=#{folioId} AND status='PENDING'
        """)
    int resolve(ExpenseRegistration e);
}
