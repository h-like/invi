package com.invi.api.template;

import com.invi.api.common.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;

    public List<TemplateDto> findAll() {
        return templateRepository.findAll().stream().map(this::toDto).toList();
    }

    public TemplateDto findById(UUID id) {
        return toDto(getOrThrow(id));
    }

    Template getOrThrow(UUID id) {
        return templateRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("템플릿을 찾을 수 없습니다: " + id));
    }

    private TemplateDto toDto(Template template) {
        return new TemplateDto(
                template.getId(), template.getName(), template.getCategory(), template.getThumbnailUrl());
    }
}
