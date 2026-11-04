package com.venuepass.mapper;

import com.venuepass.model.Models.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface VenueMapper {
    @Insert("INSERT INTO venue(name,address,capacity) VALUES(#{name},#{address},#{capacity})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Venue venue);

    @Select("SELECT * FROM venue ORDER BY id")
    List<Venue> list();

    @Select("SELECT * FROM venue WHERE id=#{id}")
    Venue get(long id);

    @Insert("INSERT INTO `event`(venue_id,title,description,active) VALUES(#{venueId},#{title},#{description},true)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertEvent(Event event);

    @Select("SELECT * FROM `event` WHERE active=true ORDER BY id")
    List<Event> events();

    @Select("SELECT * FROM `event` WHERE id=#{id}")
    Event event(long id);

    @Insert("INSERT INTO `session`(event_id,starts_at,ends_at,price,total_capacity,available,locked,hot,status) VALUES(#{eventId},#{startsAt},#{endsAt},#{price},#{totalCapacity},#{available},0,#{hot},'OPEN')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertSession(Session session);

    @Select("SELECT * FROM `session` WHERE event_id=#{eventId} ORDER BY starts_at")
    List<Session> sessions(long eventId);

    @Select("SELECT * FROM `session` WHERE id=#{id}")
    Session session(long id);

    @Select("SELECT s.* FROM `session` s WHERE s.id=#{id} FOR UPDATE")
    Session lockSession(long id);

    @Update("UPDATE `session` SET available=available-1,locked=locked+1 WHERE id=#{id} AND status='OPEN' AND available>0 AND starts_at>NOW()")
    int reserve(long id);

    @Update("UPDATE `session` SET available=available+1,locked=locked-1 WHERE id=#{id} AND locked>0")
    int release(long id);

    @Update("UPDATE `session` SET locked=locked-1 WHERE id=#{id} AND locked>0")
    int admit(long id);

    @Update("UPDATE `session` SET status='CANCELLED' WHERE id=#{id} AND status='OPEN'")
    int cancelSession(long id);
}
