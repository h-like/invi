package com.invi.api.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invi.api.account.CustomOAuth2UserService;
import com.invi.api.account.JwtAuthenticationFilter;
import com.invi.api.account.JwtService;
import com.invi.api.account.OAuth2LoginSuccessHandler;
import com.invi.api.common.NotFoundException;
import com.invi.api.common.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(TemplateController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class TemplateControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private TemplateService templateService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Test
    void get_returnsRawPresetObject_withoutAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        String preset = """
                {"blocks":[{"id":"hero-1","type":"hero","order":0,"visible":true,
                  "content":{"groomName":"샘플"},"style":{"padding":"lg"}}],
                 "design":{"theme":"classic"},"custom":{"nested":[1,true,null]}}
                """;
        when(templateService.findById(id)).thenReturn(
                new TemplateDetailDto(id, "클래식", "classic", "https://example.com/classic.webp", preset));

        String body = mvc.perform(get("/api/templates/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("클래식"))
                .andExpect(jsonPath("$.category").value("classic"))
                .andExpect(jsonPath("$.thumbnailUrl").value("https://example.com/classic.webp"))
                .andExpect(jsonPath("$.defaultPageData").isMap())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.readTree(body).get("defaultPageData")).isEqualTo(mapper.readTree(preset));
    }

    @Test
    void get_serializesMissingPresetAsNull() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.findById(id)).thenReturn(new TemplateDetailDto(id, "클래식", "classic", null, null));

        String body = mvc.perform(get("/api/templates/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonMapper.builder().build().readTree(body).get("defaultPageData").isNull()).isTrue();
    }

    @Test
    void list_remainsMetadataOnly_withoutAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.findAll()).thenReturn(List.of(new TemplateDto(id, "클래식", "classic", null)));

        mvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("클래식"))
                .andExpect(jsonPath("$[0].defaultPageData").doesNotExist());
    }

    @Test
    void get_returnsNotFound_withoutAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        when(templateService.findById(id)).thenThrow(new NotFoundException("템플릿을 찾을 수 없습니다: " + id));

        mvc.perform(get("/api/templates/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("템플릿을 찾을 수 없습니다: " + id));
    }
}
