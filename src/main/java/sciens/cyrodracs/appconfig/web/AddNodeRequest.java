package sciens.cyrodracs.appconfig.web;

public record AddNodeRequest(
        Long parentObjectId,
        String typeCode,
        String code,
        String enumValue
) {}
