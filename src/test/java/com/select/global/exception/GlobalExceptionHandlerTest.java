package com.select.global.exception;

import com.select.global.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void NO_INPUT_RETURN_400() throws Exception {
        mockMvc.perform(post("/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": ""
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }

    @Test
    void INPUT_OK_RETURN_SUCCESS() throws Exception {
        mockMvc.perform(post("/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                           {
                              "name" : "테스트"
                           }
                        """))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestController {
        @PostMapping("/test")
        String test(@Valid @RequestBody TestRequest request){
            return request.name;
        }
    }

    record TestRequest(
            @NotBlank(message = "이름은 필수입니다.")
            String name
    ) {

    }
}
