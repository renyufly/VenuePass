package com.venuepass.mapper;

import com.venuepass.model.Models.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {
    @Insert("INSERT INTO reservation(session_id,username,participant_name,status,amount,code,expires_at,created_at) VALUES(#{sessionId},#{username},#{participantName},#{status},#{amount},#{code},#{expiresAt},#{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Reservation reservation);

    @Select("SELECT * FROM reservation WHERE id=#{id}")
    Reservation get(long id);

    @Select("SELECT * FROM reservation WHERE id=#{id} FOR UPDATE")
    Reservation lock(long id);

    @Select("SELECT * FROM reservation WHERE session_id=#{sessionId} AND username=#{username}")
    Reservation byUser(@Param("sessionId") long sessionId, @Param("username") String username);

    @Select("SELECT * FROM reservation WHERE code=#{code}")
    Reservation byCode(String code);

    @Select("SELECT * FROM reservation WHERE username=#{username} ORDER BY id DESC")
    List<Reservation> forUser(String username);

    @Select("SELECT id FROM reservation WHERE status='PENDING_PAYMENT' AND expires_at<=#{now} LIMIT 100")
    List<Long> expired(LocalDateTime now);

    @Select("SELECT id FROM reservation WHERE session_id=#{sessionId} AND status IN ('PENDING_PAYMENT','PAID')")
    List<Long> activeForSession(long sessionId);

    @Update("UPDATE reservation SET status=#{next} WHERE id=#{id} AND status=#{previous}")
    int transition(@Param("id") long id, @Param("previous") String previous, @Param("next") String next);

    @Insert("INSERT INTO reservation_event(reservation_id,old_status,new_status,actor,occurred_at) VALUES(#{id},#{previous},#{next},#{actor},NOW())")
    void event(@Param("id") long id, @Param("previous") String previous, @Param("next") String next, @Param("actor") String actor);

    @Insert("INSERT INTO verification(reservation_id,operator_name,device,verified_at) VALUES(#{id},#{operator},#{device},NOW())")
    void verify(@Param("id") long id, @Param("operator") String operator, @Param("device") String device);

    @Update("UPDATE payment SET refund_status='PENDING' WHERE reservation_id=#{id} AND refund_status='NONE'")
    int requestRefund(long id);

    @Update("UPDATE payment SET refund_status='DONE', refund_reference=#{reference} WHERE reservation_id=#{id} AND refund_status='PENDING'")
    int finishRefund(@Param("id") long id, @Param("reference") String reference);

    @Insert("INSERT INTO inventory_release(reservation_id,session_id,username) VALUES(#{reservationId},#{sessionId},#{username})")
    void queueRelease(@Param("reservationId") long reservationId, @Param("sessionId") long sessionId, @Param("username") String username);

    @Select("SELECT reservation_id,session_id,username FROM inventory_release WHERE processed=false LIMIT 100")
    List<InventoryRelease> pendingReleases();

    @Update("UPDATE inventory_release SET processed=true WHERE reservation_id=#{id}")
    void finishRelease(long id);

    @Select("SELECT r.session_id AS sessionId, COUNT(*) AS reservations, SUM(r.status IN ('PAID','CHECKED_IN')) AS paid, SUM(r.status='CHECKED_IN') AS checkedIn, COALESCE(SUM(CASE WHEN r.status IN ('PAID','CHECKED_IN') THEN r.amount ELSE 0 END),0) AS revenue FROM reservation r WHERE r.session_id=#{sessionId} GROUP BY r.session_id")
    Report report(long sessionId);
}
