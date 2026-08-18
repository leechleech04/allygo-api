package com.allygo.allygo_api.auth.phoneverification;

import com.allygo.allygo_api.auth.phoneverification.application.PhoneVerificationCommandService;
import com.allygo.allygo_api.auth.phoneverification.application.result.PhoneVerificationSentResult;
import com.allygo.allygo_api.auth.phoneverification.application.result.PhoneVerificationConfirmedResult;
import com.allygo.allygo_api.auth.phoneverification.domain.PhoneVerificationException;
import com.allygo.allygo_api.auth.phoneverification.domain.VerificationPurpose;
import com.allygo.allygo_api.auth.phoneverification.presentation.PhoneVerificationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationControllerTest {
    @Mock PhoneVerificationCommandService commandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PhoneVerificationController(commandService)).build();
    }

    @Test
    void returnsDocumentedSuccessResponse() throws Exception {
        when(commandService.send("+821012345678", "SIGN_UP", null)).thenReturn(
                new PhoneVerificationSentResult(
                        10241L, Instant.parse("2026-08-12T03:03:00Z"),
                        180, 60, "+82 10-****-5678"
                )
        );

        mockMvc.perform(post("/api/auth/phone-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+821012345678","purpose":"SIGN_UP"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_SENT"))
                .andExpect(jsonPath("$.data.verificationId").value(10241))
                .andExpect(jsonPath("$.data.expiresIn").value(180))
                .andExpect(jsonPath("$.data.resendAvailableIn").value(60))
                .andExpect(jsonPath("$.data.maskedPhoneNumber").value("+82 10-****-5678"));
    }

    @Test
    void returnsRetryAfterForCooldown() throws Exception {
        when(commandService.send("+821012345678", "SIGN_UP", null))
                .thenThrow(PhoneVerificationException.resendTooEarly(42));

        mockMvc.perform(post("/api/auth/phone-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+821012345678","purpose":"SIGN_UP"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VERIFICATION_RESEND_TOO_EARLY"))
                .andExpect(jsonPath("$.data.retryAfter").value(42));
    }

    @Test
    void returnsDocumentedConfirmationResponse() throws Exception {
        when(commandService.confirm("10241", "482913", null)).thenReturn(
                new PhoneVerificationConfirmedResult(
                        10241L, "+821012345678", VerificationPurpose.SIGN_UP, true,
                        Instant.parse("2026-08-12T03:05:00Z"), "pvt_token", 600
                )
        );

        mockMvc.perform(post("/api/auth/phone-verifications/10241/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"verificationCode":"482913"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_COMPLETED"))
                .andExpect(jsonPath("$.data.verificationId").value(10241))
                .andExpect(jsonPath("$.data.phoneNumber").value("+821012345678"))
                .andExpect(jsonPath("$.data.purpose").value("SIGN_UP"))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.verificationToken").value("pvt_token"))
                .andExpect(jsonPath("$.data.tokenExpiresIn").value(600));
    }

    @Test
    void returnsRemainingAttemptsForMismatchedCode() throws Exception {
        when(commandService.confirm("10241", "111111", null))
                .thenThrow(PhoneVerificationException.verificationCodeMismatch(3));

        mockMvc.perform(post("/api/auth/phone-verifications/10241/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"verificationCode":"111111"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"))
                .andExpect(jsonPath("$.data.remainingAttempts").value(3));
    }
}
