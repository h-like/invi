package com.invi.api.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.invi.api.common.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateRepository templateRepository;

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService(templateRepository);
    }

    @Test
    void findAll_mapsEntitiesToDtos() {
        Template classic = Template.builder().name("클래식").category("classic").build();
        when(templateRepository.findAll()).thenReturn(List.of(classic));

        List<TemplateDto> result = templateService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("클래식");
        assertThat(result.get(0).category()).isEqualTo("classic");
    }

    @Test
    void findById_throwsNotFound_whenTemplateMissing() {
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.findById(id)).isInstanceOf(NotFoundException.class);
    }
}
