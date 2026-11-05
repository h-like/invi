package com.invi.api.common;

/**
 * A save based on an older copy than the one stored (another tab or device saved first).
 * A 409 like any conflict, but tagged with {@link #CODE} so the editor can tell it apart from
 * other conflicts (e.g. fenced media) and offer the latest copy instead of a plain retry.
 */
public class StaleVersionException extends ConflictException {

    public static final String CODE = "STALE_VERSION";
    public static final String MESSAGE =
            "다른 곳에서 먼저 저장된 내용이 있어 저장하지 않았습니다. 최신 내용을 확인한 뒤 다시 편집해 주세요.";

    public StaleVersionException() {
        super(MESSAGE);
    }
}
