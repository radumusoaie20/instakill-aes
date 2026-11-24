package com.instakill.llm.domain;

import com.instakill.common.error.InstakillException;

public class LlmServiceException extends InstakillException {

    public LlmServiceException(String message) {
        super(message);
    }

    public LlmServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
