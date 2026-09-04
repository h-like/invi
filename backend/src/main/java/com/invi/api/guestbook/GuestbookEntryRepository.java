package com.invi.api.guestbook;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestbookEntryRepository extends JpaRepository<GuestbookEntry, UUID> {

    List<GuestbookEntry> findByInvitationIdAndDeletedAtIsNull(UUID invitationId);
}
