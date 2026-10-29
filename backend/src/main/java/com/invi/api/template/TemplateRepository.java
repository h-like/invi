package com.invi.api.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    List<Template> findByCategory(String category);

    Optional<Template> findByCode(String code);

    /** Rows seeded before {@code code} existed, matched by their original category. */
    Optional<Template> findFirstByCodeIsNullAndCategoryOrderByCreatedAtAsc(String category);
}
