package com.kbase.ai.provider.port;

import com.kbase.ai.provider.model.AiChatRequest;
import com.kbase.ai.provider.model.AiChatResult;

/** KBase-owned boundary for bounded text generation. */
public interface AiChatModel {

    AiChatResult generate(AiChatRequest request);
}
