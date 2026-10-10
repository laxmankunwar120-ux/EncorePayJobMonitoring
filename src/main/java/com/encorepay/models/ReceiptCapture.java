package com.encorepay.models;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ReceiptCapture {

    public static final int UNKNOWN_COUNT = -1;

    public int totalCount = UNKNOWN_COUNT;
    public final Map<String, Integer> reasonCounts = new LinkedHashMap<>();
    public final Set<String> problems = new LinkedHashSet<>();

    public ReceiptCapture() {
    }
}
