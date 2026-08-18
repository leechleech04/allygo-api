package com.allygo.allygo_api.auth.phoneverification.presentation.request;

public record SendPhoneVerificationRequest(String phoneNumber, String purpose) {
}
