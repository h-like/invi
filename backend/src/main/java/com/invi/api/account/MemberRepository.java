package com.invi.api.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
