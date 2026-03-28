package sciens.cyrodracs;

import java.util.List;

public record DataFormElement(
        String key,
        String label,
        String type,
        List<String> options,
        int cols,
        boolean breakBefore,
        Double min,
        Double max,
        Integer rows
) {}
