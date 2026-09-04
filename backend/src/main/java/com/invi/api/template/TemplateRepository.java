package com.invi.api.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    List<Template> findByCategory(String category);
}
