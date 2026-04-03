package sciens.cyrodracs.appconfig;

public enum DataFormEntityType {

    CAMERA_PRODUCER("sciens.cyrodracs.camera.CameraProducer");

    private final String fqcn;

    DataFormEntityType(String fqcn) {
        this.fqcn = fqcn;
    }

    public String getFqcn() { return fqcn; }
}
