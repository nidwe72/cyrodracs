package sciens.cyrodracs.appconfig.web;

import java.util.List;
import java.util.Map;

public record PagedResponse(
        List<Map<String, Object>> items,
        long totalCount,
        int page,
        int pageSize,
        int totalPages
) {}
