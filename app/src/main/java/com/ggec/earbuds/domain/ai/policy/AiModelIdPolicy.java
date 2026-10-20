package com.ggec.earbuds.domain.ai.policy;

import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;

/**
 * 手动添加的模型名会原样放进请求体。模型名里常见斜杠、冒号、点和下划线，唯独不会有空白。
 */
public final class AiModelIdPolicy {

    static final int MAX_LENGTH = 200;

    public String normalize(String raw) {
        String id = raw == null ? "" : raw.strip();
        if (id.isEmpty()) {
            throw new AiValidationException(AiValidationError.MODEL_ID_EMPTY);
        }
        if (id.length() > MAX_LENGTH) {
            throw new AiValidationException(AiValidationError.MODEL_ID_INVALID);
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                throw new AiValidationException(AiValidationError.MODEL_ID_INVALID);
            }
        }
        return id;
    }
}
