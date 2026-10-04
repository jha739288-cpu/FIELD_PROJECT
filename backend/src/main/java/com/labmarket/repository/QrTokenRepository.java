package com.labmarket.repository;

import com.labmarket.entity.QrToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link QrToken}. Lookups are always by hash — raw tokens never hit the DB. */
public interface QrTokenRepository extends JpaRepository<QrToken, Long> {

  Optional<QrToken> findByTokenHash(String tokenHash);

  List<QrToken> findByBookingIdAndUsedFalse(Long bookingId);
}
