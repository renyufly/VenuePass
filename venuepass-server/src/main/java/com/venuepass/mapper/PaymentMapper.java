package com.venuepass.mapper;

import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;

@Mapper
public interface PaymentMapper {
    @Insert("INSERT INTO payment(reservation_id,external_reference,amount,paid_at) VALUES(#{id},#{reference},#{amount},NOW())")
    void insert(@Param("id") long id, @Param("reference") String reference, @Param("amount") BigDecimal amount);
}
