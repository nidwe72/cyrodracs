package sciens.cyrodracs.appconfig;

public enum DataFormEntityType {

    CAMERA_PRODUCER("sciens.cyrodracs.camera.CameraProducer"),
    CAMERA_LENS_MOUNT("sciens.cyrodracs.camera.CameraLensMount"),
    CAMERA("sciens.cyrodracs.camera.Camera");

    private final String fqcn;

    DataFormEntityType(String fqcn) {
        this.fqcn = fqcn;
    }

    public String getFqcn() { return fqcn; }
}
