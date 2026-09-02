package com.allygo.allygo_api.auth.account.presentation.request;

import com.allygo.allygo_api.auth.account.application.command.FindLoginIdCommand;

public record FindLoginIdRequest(String verificationToken) {
    public FindLoginIdCommand toCommand() {
        return new FindLoginIdCommand(verificationToken);
    }
}
