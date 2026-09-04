package com.invi.api.invitation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
