package com.sagar.emailsender.model;

import java.util.List;

public record DraftResponse(int attempted, int created, int failed, List<DraftItemResult> results) {
}
