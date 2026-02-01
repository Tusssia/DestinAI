package com.destinai.modules.auth.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpTokenRepository extends JpaRepository<OtpTokenEntity, UUID> {
	@Query("""
			select o from OtpTokenEntity o
			where lower(o.email) = lower(:email)
			order by o.createdAt desc
			""")
	List<OtpTokenEntity> findLatestByEmail(@Param("email") String email);

	@Query("""
			select count(o) from OtpTokenEntity o
			where lower(o.email) = lower(:email)
			  and o.createdAt >= :since
			""")
	long countByEmailSince(@Param("email") String email, @Param("since") Instant since);

	@Query("""
			select o from OtpTokenEntity o
			where o.requestIpHash = :ipHash
			order by o.createdAt desc
			""")
	List<OtpTokenEntity> findLatestByIpHash(@Param("ipHash") String ipHash);

	@Query("""
			select count(o) from OtpTokenEntity o
			where o.requestIpHash = :ipHash
			  and o.createdAt >= :since
			""")
	long countByIpHashSince(@Param("ipHash") String ipHash, @Param("since") Instant since);

	@Query("""
			select o from OtpTokenEntity o
			where lower(o.email) = lower(:email)
			  and o.consumedAt is null
			  and o.expiresAt > :now
			order by o.createdAt desc
			""")
	List<OtpTokenEntity> findActiveByEmail(@Param("email") String email, @Param("now") Instant now);

	@Modifying
	@Query("""
			update OtpTokenEntity o
			set o.consumedAt = :consumedAt
			where o.id = :id
			  and o.consumedAt is null
			  and o.expiresAt > :now
			""")
	int consumeIfActive(@Param("id") UUID id, @Param("consumedAt") Instant consumedAt,
			@Param("now") Instant now);
}

