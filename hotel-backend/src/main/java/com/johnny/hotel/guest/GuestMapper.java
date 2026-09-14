package com.johnny.hotel.guest;
import org.apache.ibatis.annotations.*;import java.util.List;
@Mapper public interface GuestMapper {
 @Insert("""
 INSERT INTO guest_profile(linked_user_id,first_name,last_name,phone,email,gender,date_of_birth,nationality,document_type,document_number,issuing_country,document_expiry_date,notes,status)
 VALUES(#{linkedUserId},#{firstName},#{lastName},#{phone},#{email},#{gender},#{dateOfBirth},#{nationality},#{documentType},#{documentNumber},#{issuingCountry},#{documentExpiryDate},#{notes},#{status})
 """) @Options(useGeneratedKeys=true,keyProperty="id") int insertProfile(GuestProfile p);
 @Select("SELECT * FROM guest_profile WHERE id=#{id}") GuestProfile profile(Long id);
 @Select("SELECT * FROM guest_profile WHERE id=#{id} FOR UPDATE") GuestProfile lockProfile(Long id);
 @Select("SELECT * FROM guest_profile WHERE linked_user_id=#{userId}") GuestProfile byUser(Long userId);
 @Update("""
 UPDATE guest_profile SET first_name=#{firstName},last_name=#{lastName},phone=#{phone},email=#{email},gender=#{gender},date_of_birth=#{dateOfBirth},nationality=#{nationality},document_type=#{documentType},document_number=#{documentNumber},issuing_country=#{issuingCountry},document_expiry_date=#{documentExpiryDate},notes=#{notes},update_time=NOW() WHERE id=#{id}
 """) int updateProfile(GuestProfile p);
 @Select("""
 <script>SELECT * FROM guest_profile WHERE status=1 <if test='q != null and q != ""'>AND (CONCAT(first_name,' ',last_name) LIKE CONCAT('%',#{q},'%') OR phone LIKE CONCAT('%',#{q},'%') OR email LIKE CONCAT('%',#{q},'%') OR document_number LIKE CONCAT('%',#{q},'%'))</if> ORDER BY id DESC LIMIT 100</script>
 """) List<GuestProfile> search(@Param("q") String q);
 @Select("""
 SELECT * FROM guest_profile WHERE ((#{p.documentType} IS NOT NULL AND #{p.documentNumber} IS NOT NULL AND document_type=#{p.documentType} AND document_number=#{p.documentNumber}) OR (#{p.email} IS NOT NULL AND email=#{p.email}) OR (#{p.phone} IS NOT NULL AND phone=#{p.phone}) OR (first_name=#{p.firstName} AND last_name=#{p.lastName} AND #{p.dateOfBirth} IS NOT NULL AND date_of_birth=#{p.dateOfBirth})) ORDER BY CASE WHEN document_type=#{p.documentType} AND document_number=#{p.documentNumber} THEN 0 ELSE 1 END,id LIMIT 20
 """) List<GuestProfile> duplicates(@Param("p") GuestProfile p);
 @Insert("INSERT INTO booking_guest(booking_id,guest_id,guest_role) VALUES(#{bookingId},#{guestId},#{guestRole})") @Options(useGeneratedKeys=true,keyProperty="id") int add(BookingGuest bg);
 @Select("SELECT * FROM booking_guest WHERE booking_id=#{bookingId} ORDER BY guest_role,id") List<BookingGuest> bookingGuests(Long bookingId);
 @Select("SELECT * FROM booking_guest WHERE booking_id=#{bookingId} AND guest_role=0") BookingGuest primary(Long bookingId);
 @Select("SELECT COUNT(*) FROM booking_guest WHERE booking_id=#{bookingId}") int count(Long bookingId);
 @Delete("DELETE FROM booking_guest WHERE id=#{id} AND booking_id=#{bookingId}") int remove(@Param("bookingId")Long bookingId,@Param("id")Long id);
 @Select("SELECT * FROM guest_registration WHERE booking_id=#{bookingId}") GuestRegistration registration(Long bookingId);
 @Insert("INSERT INTO guest_registration(booking_id,primary_guest_id,registered_by,registered_time,registration_confirmed) VALUES(#{bookingId},#{primaryGuestId},#{registeredBy},#{registeredTime},#{registrationConfirmed})") @Options(useGeneratedKeys=true,keyProperty="id") int insertRegistration(GuestRegistration r);
 @Select("""
 SELECT bg.booking_id AS bookingId,a.id AS assignmentId,r.room_number AS roomNumber,rt.type_name AS roomTypeName,a.start_time AS actualCheckInTime,a.end_time AS actualCheckOutTime FROM booking_guest bg JOIN booking_room_assignment a ON a.booking_id=bg.booking_id JOIN room r ON r.id=a.room_id JOIN room_type rt ON rt.id=a.room_type_id WHERE bg.guest_id=#{guestId} AND a.end_time IS NOT NULL ORDER BY a.start_time DESC
 """) List<GuestViews.Stay> stays(Long guestId);
}
