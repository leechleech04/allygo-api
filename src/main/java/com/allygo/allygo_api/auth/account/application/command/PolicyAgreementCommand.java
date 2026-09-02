package com.allygo.allygo_api.auth.account.application.command;

public record PolicyAgreementCommand(Long policyDocumentId, Boolean agreed) {
}
