package com.invi.api.rsvp;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RsvpRepository extends JpaRepository<Rsvp, UUID> {

    List<Rsvp> findByInvitationId(UUID invitationId);
}
