package com.allygo.allygo_api.auth.account.presentation.request;

import com.allygo.allygo_api.auth.account.application.command.PolicyAgreementCommand;

public record PolicyAgreementRequest(Long policyDocumentId, Boolean agreed) {
    public PolicyAgreementCommand toCommand() {
        return new PolicyAgreementCommand(policyDocumentId, agreed);
    }
}
