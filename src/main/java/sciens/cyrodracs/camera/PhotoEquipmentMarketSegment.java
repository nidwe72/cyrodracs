package sciens.cyrodracs.camera;

/**
 * Market-tier segmentation for photo equipment per
 * {@code domainEntities.md} *Enumerations → PhotoEquipmentMarketSegment*.
 *
 * <p>Shared across photo-equipment types — currently consumed by
 * {@link Camera#getPhotoEquipmentMarketSegment()}; future tripod / lens
 * entities are expected to carry the same enum.</p>
 */
public enum PhotoEquipmentMarketSegment {
    ENTRY_LEVEL,
    ENTHUSIAST,
    PROSUMER,
    PROFESSIONAL
}
